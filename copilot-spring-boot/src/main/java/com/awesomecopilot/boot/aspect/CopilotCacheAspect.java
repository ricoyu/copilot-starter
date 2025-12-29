package com.awesomecopilot.boot.aspect;

import com.awesomecopilot.boot.annotation.CacheEvict;
import com.awesomecopilot.cache.JedisUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.awesomecopilot.common.lang.utils.DateUtils.format;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * <p>
 * Copyright: (C), 2020-09-10 14:30
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Aspect
@Order(1)
public class CopilotCacheAspect {

	private Logger log = LoggerFactory.getLogger(CopilotCacheAspect.class);

	// 定义定时线程池（建议全局单例，避免重复创建）
	// 全局单例线程池，建议使用ThreadPoolExecutor自定义，避免Executors默认配置问题
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable);
		thread.setName("cache-evict-scheduler");
		thread.setDaemon(true); // 守护线程，避免阻塞应用关闭
		return thread;
	});

	@Pointcut("@annotation(com.awesomecopilot.boot.annotation.CacheEvict)")
	public void pointcut() {

	}

	@Before("pointcut()")
	public void before(JoinPoint joinPoint) throws Throwable {
		//根据joinPoint获取CacheEvict注解
		CacheEvict cacheEvict = getCacheEvictAnnotation(joinPoint);
		String[] keys = cacheEvict.keys();
		String time = format(new Date());
		for (int i = 0; i < keys.length; i++) {
			String key = keys[i];
			log.info("{} 第一次删除缓存: {}", time, key);
			try {
				JedisUtils.del(key);
			} catch (Exception e) {
				//为了不影响业务主流程, 黑伞缓存失败只输出错误日志
				log.error("{} 删除缓存失败: {}", format(new Date()), key, e);
			}
		}
	}

	@AfterReturning("pointcut()")
	public void afterReturning(JoinPoint joinPoint) throws Throwable {
		//根据joinPoint获取CacheEvict注解
		CacheEvict cacheEvict = getCacheEvictAnnotation(joinPoint);
		scheduler.schedule(() -> {
			String time = format(new Date());
			String[] keys = cacheEvict.keys();
			for (int i = 0; i < keys.length; i++) {
				String key = keys[i];
				log.info("{} 延迟1秒再删一遍缓存: {}", time, key);
				try {
					JedisUtils.del(key);
				} catch (Exception e) {
					//为了不影响业务主流程, 黑伞缓存失败只输出错误日志
					log.error("{} 删除缓存失败: {}", format(new Date()), key, e);
				}
			}
		}, 1, SECONDS);
	}

	/**
	 * 从JoinPoint中获取方法上的@CacheEvict注解
	 */
	private CacheEvict getCacheEvictAnnotation(JoinPoint joinPoint) {
		// 1. 强转为MethodSignature（因为切入点是方法注解）
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		// 2. 获取方法对象
		// 3. 获取方法上的注解
		return signature.getMethod().getAnnotation(CacheEvict.class);
	}
}
