package com.awesomecopilot.boot.aspect;

import com.awesomecopilot.boot.annotation.CacheEvict;
import com.awesomecopilot.cache.JedisUtils;
import jakarta.annotation.PreDestroy;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.awesomecopilot.common.lang.utils.DateUtils.format;

/**
 * @CacheEvict 延时双删切面.
 * <p>
 * 时序: 方法执行前删第一遍({@code @Before}), 方法成功返回后延迟 N 秒再删第二遍
 * ({@code @AfterReturning}), 用第二删清掉「先删缓存 → 读旧库 → 回填旧值」窗口里写回去的脏数据.
 * <p>
 * 延迟秒数: 全局默认由 copilot.cache.evict-delay-seconds 配置, 单个方法可用注解属性
 * evictDelaySeconds 覆盖(主从复制延迟大的场景按方法调大).
 * <p/>
 * Copyright: Copyright (c) 2025-12-13
 * <p/>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
@Aspect
@Order(1)
public class CopilotCacheEvictAspect {

	private static final Logger log = LoggerFactory.getLogger(CopilotCacheEvictAspect.class);
	
	/** 全局默认延迟秒数 */
	private final long evictDelaySeconds;
	
	/** 排队上限值, 仅用于日志展示 */
	private final int maxPendingTasks;
	
	/**
	 * 延迟任务排队名额. 第二删是补偿动作, Redis 变慢时不允许任务无界堆积
	 * (单线程调度器配无界队列, 在 socketTimeout/maxWaitMillis 较长的配置下越积越多,
	 * 第二删会远迟于声明的延迟, 双删的一致性窗口失效), 名额用完直接跳过并记 error.
	 */
	private final Semaphore pendingSlots;
	
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable);
		thread.setName("cache-evict-scheduler");
		thread.setDaemon(true);
		return thread;
	});
	
	private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();
	
	/** 从字节码/反射信息发现方法参数名(-parameters 编译时可用) */
	private static final ParameterNameDiscoverer PARAM_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
	
	public CopilotCacheEvictAspect() {
		this(1L, 1000);
	}
	
	public CopilotCacheEvictAspect(long evictDelaySeconds, int maxPendingTasks) {
		this.evictDelaySeconds = evictDelaySeconds > 0 ? evictDelaySeconds : 1L;
		this.maxPendingTasks = maxPendingTasks > 0 ? maxPendingTasks : 1000;
		this.pendingSlots = new Semaphore(this.maxPendingTasks);
	}
	
	@Pointcut("@annotation(com.awesomecopilot.boot.annotation.CacheEvict)")
	public void pointcut() {}
	
	@Before("pointcut()")
	public void before(JoinPoint joinPoint) throws Throwable {
		CacheEvict cacheEvict = resolveCacheEvict(joinPoint);
		if (cacheEvict == null) {
			return;
		}
		List<String> realKeys = resolveKeysQuietly(joinPoint, cacheEvict.keys(), "第一次删除缓存");
		if (!realKeys.isEmpty()) {
			evictKeys("第一次删除缓存", realKeys);
		}
	}
	
	@AfterReturning("pointcut()")
	public void afterReturning(JoinPoint joinPoint) throws Throwable {
		CacheEvict cacheEvict = resolveCacheEvict(joinPoint);
		if (cacheEvict == null) {
			return;
		}
		tryScheduleSecondDelete(joinPoint, cacheEvict);
	}
	
	/**
	 * 安排第二遍删除. 返回 false 表示被跳过(排队名额用完, 或调度器已停止).
	 * key 在主线程提前解析, 避免异步线程拿不到调用上下文.
	 */
	boolean tryScheduleSecondDelete(JoinPoint joinPoint, CacheEvict cacheEvict) {
		if (!pendingSlots.tryAcquire()) {
			log.error("缓存第二删被跳过: 排队任务已达上限({}), 方法 {}#{}, keys={} —— 缓存里可能有脏数据残留, 请检查 Redis 响应耗时",
				maxPendingTasks,
				joinPoint.getSignature().getDeclaringTypeName(),
				joinPoint.getSignature().getName(),
				String.join(",", cacheEvict.keys()));
			return false;
		}
		
		List<String> realKeys = resolveKeysQuietly(joinPoint, cacheEvict.keys(), "延迟第二删");
		if (realKeys.isEmpty()) {
			pendingSlots.release();
			return true;
		}
		long delay = secondDeleteDelaySeconds(cacheEvict, evictDelaySeconds);
		try {
			scheduler.schedule(() -> {
				try {
					evictKeys("延迟第二删", realKeys);
				} finally {
					pendingSlots.release();
				}
			}, delay, TimeUnit.SECONDS);
		} catch (RejectedExecutionException e) {
			//停机窗口(@PreDestroy 已执行但仍有在途请求走到 @AfterReturning)或手工调用 shutdown 后,
			//schedule 必然拒绝任务. 此处归还名额(任务从未入队, 任务体的 finally 不会运行),
			//且不向上抛——@CacheEvict 的契约是删除失败不影响业务方法本身
			pendingSlots.release();
			log.error("缓存第二删被跳过: 调度器已停止, 方法 {}#{}, keys={}",
				joinPoint.getSignature().getDeclaringTypeName(),
				joinPoint.getSignature().getName(),
				String.join(",", cacheEvict.keys()));
			return false;
		}
		return true;
	}
	
	/**
	 * 应用停止时给排队的第二删最多 5 秒收尾机会. 调度线程是 daemon, JVM 退出不会等待它,
	 * 不收尾的话未执行的第二删直接丢失, 脏缓存要等自然过期. 收尾不完的部分记 warn 说明.
	 */
	@PreDestroy
	public void shutdown() {
		scheduler.shutdown();
		try {
			if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
				List<Runnable> dropped = scheduler.shutdownNow();
				log.warn("应用停止, 还有 {} 个缓存第二删任务未执行, 这部分缓存依赖自然过期", dropped.size());
			}
		} catch (InterruptedException e) {
			scheduler.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
	
	/** 实际执行删除; 单键失败只记日志, 不影响业务主流程(注解契约). 测试通过覆写本方法隔离 Redis */
	void evictKeys(String stage, List<String> keys) {
		String time = format(new Date());
		for (String key : keys) {
			log.info("{} {}: {}", time, stage, key);
			try {
				JedisUtils.del(key);
			} catch (Exception e) {
				log.error("{} 删除缓存失败: {}", format(new Date()), key, e);
			}
		}
	}
	
	/**
	 * 取 @CacheEvict 注解. JDK 动态代理下 signature.getMethod() 是接口方法, 注解写在实现类上时
	 * 直接 getAnnotation 返回 null(修复前此处为 NPE 源头), 这里换算成实现类的具体方法再查;
	 * 也支持注解写在接口方法、实现类 override 的写法(AnnotationUtils 会向上查找).
	 */
	static CacheEvict resolveCacheEvict(JoinPoint joinPoint) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		java.lang.reflect.Method method = signature.getMethod();
		CacheEvict cacheEvict = AnnotationUtils.findAnnotation(method, CacheEvict.class);
		if (cacheEvict == null && joinPoint.getTarget() != null) {
			method = AopUtils.getMostSpecificMethod(method, joinPoint.getTarget().getClass());
			cacheEvict = AnnotationUtils.findAnnotation(method, CacheEvict.class);
		}
		if (cacheEvict == null) {
			log.warn("@CacheEvict 切面命中但方法 {}#{} 上找不到注解, 跳过缓存删除",
				signature.getDeclaringTypeName(), signature.getName());
		}
		return cacheEvict;
	}
	
	private List<String> resolveKeysQuietly(JoinPoint joinPoint, String[] originKeys, String stage) {
		try {
			MethodSignature signature = (MethodSignature) joinPoint.getSignature();
			Method method = signature.getMethod();
			String[] paramNames = signature.getParameterNames();
			// JDK 动态代理下 signature.getMethod() 是接口方法, 其参数名取自接口——接口(api 包)不带
			// -parameters 编译时拿到的是 arg0/arg1, 模板里的 #dto 之类变量解析不出来.
			// 与注解查找一致地换算到实现类的具体方法, 用 Spring 的参数名发现器再取一次
			if (joinPoint.getTarget() != null) {
				Method specific = AopUtils.getMostSpecificMethod(method, joinPoint.getTarget().getClass());
				if (!specific.equals(method)) {
					String[] specificNames = PARAM_NAME_DISCOVERER.getParameterNames(specific);
					if (specificNames != null && specificNames.length > 0 && !specificNames[0].startsWith("arg")) {
						method = specific;
						paramNames = specificNames;
					}
				}
			}
			return resolveEvictKeys(method, paramNames, joinPoint.getArgs(), originKeys);
		} catch (Exception e) {
			log.error("{}: key 解析整体失败, 本次删除跳过, 模板={}", stage, String.join(",", originKeys), e);
			return Collections.emptyList();
		}
	}
	
	/**
	 * 解析 key 模板(#{...} 为 SpEL 表达式), 返回可安全执行删除的 key 列表.
	 * <p>
	 * 同时支持两种模板写法: {@code #{#dto.brandId}}(参数变量, #名字即方法参数名)与
	 * {@code #{dto.brandId}}(属性路径, 以「参数名→参数值」Map 作根对象). 不再用
	 * DynamicUtils 每次调用现造动态类(热点写接口下 metaspace 持续增长).
	 * <p>
	 * 解析不出来的模板一律跳过、绝不回退成模板原文去执行删除: 参数名拿不到(Spring 6 不再从字节码的 LocalVariableTable
	 * 读参数名, 业务模块未开 -parameters 编译时 paramNames 为 null)或
	 * 单个模板求值失败, 该 key 被跳过并记 error——日志里不会再出现删了不存在的 key 却显示"已删除"的假象.
	 */
	static List<String> resolveEvictKeys(java.lang.reflect.Method method, String[] paramNames, Object[] args, String[] originKeys) {
		if (originKeys == null || originKeys.length == 0) {
			return Collections.emptyList();
		}
		
		StandardEvaluationContext context = new StandardEvaluationContext();
		context.addPropertyAccessor(new MapAccessor());
		Map<String, Object> paramMap = new HashMap<>();
		boolean hasParams = paramNames != null && args != null && paramNames.length == args.length;
		if (hasParams) {
			for (int i = 0; i < args.length; i++) {
				if (paramNames[i] != null) {
					context.setVariable(paramNames[i], args[i]);
					paramMap.put(paramNames[i], args[i]);
				}
			}
		}
		context.setRootObject(paramMap);
		
		List<String> resolved = new ArrayList<>(originKeys.length);
		for (String tpl : originKeys) {
			if (tpl == null || !tpl.contains("#{")) {
				//不含模板的 key 是固定字符串, 直接可用
				resolved.add(tpl);
				continue;
			}
			if (!hasParams) {
				log.error("缓存key模板 '{}' 需要方法参数名, 但拿不到(业务模块请开 -parameters 编译), 本次删除跳过. 方法: {}#{}",
					tpl, method.getDeclaringClass().getSimpleName(), method.getName());
				continue;
			}
			try {
				Expression exp = SPEL_PARSER.parseExpression(tpl, new TemplateParserContext());
				Object value = exp.getValue(context);
				if (value == null) {
					log.error("缓存key模板 '{}' 求值为 null, 本次删除跳过", tpl);
					continue;
				}
				resolved.add(value.toString());
			} catch (Exception e) {
				log.error("缓存key模板 '{}' 解析失败, 本次删除跳过(不回退删模板原文), 原因: {}", tpl, e.getMessage());
			}
		}
		return resolved;
	}
	
	static long secondDeleteDelaySeconds(CacheEvict cacheEvict, long globalDefaultSeconds) {
		long perMethod = cacheEvict.evictDelaySeconds();
		return perMethod > 0 ? perMethod : globalDefaultSeconds;
	}
}
