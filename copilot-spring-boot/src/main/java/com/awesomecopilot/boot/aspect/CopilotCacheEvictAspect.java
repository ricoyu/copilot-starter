package com.awesomecopilot.boot.aspect;

import com.awesomecopilot.boot.annotation.CacheEvict;
import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.common.lang.utils.DynamicUtils;
import com.awesomecopilot.common.spring.utils.SpElUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.awesomecopilot.common.lang.utils.DateUtils.format;
import static java.util.concurrent.TimeUnit.SECONDS;

@Aspect
@Order(1)
public class CopilotCacheEvictAspect {

	private Logger log = LoggerFactory.getLogger(CopilotCacheEvictAspect.class);

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable);
		thread.setName("cache-evict-scheduler");
		thread.setDaemon(true);
		return thread;
	});

	@Pointcut("@annotation(com.awesomecopilot.boot.annotation.CacheEvict)")
	public void pointcut() {}

	@Before("pointcut()")
	public void before(JoinPoint joinPoint) throws Throwable {
		CacheEvict cacheEvict = getCacheEvictAnnotation(joinPoint);
		String[] realKeys = parseSpelKeys(joinPoint, cacheEvict.keys());
		String time = format(new Date());
		for (String key : realKeys) {
			log.info("{} 第一次删除缓存: {}", time, key);
			try {
				JedisUtils.del(key);
			} catch (Exception e) {
				log.error("{} 删除缓存失败: {}", format(new Date()), key, e);
			}
		}
	}

	@AfterReturning("pointcut()")
	public void afterReturning(JoinPoint joinPoint) throws Throwable {
		CacheEvict cacheEvict = getCacheEvictAnnotation(joinPoint);
		// 提前解析（主线程中完成，避免异步上下文丢失）
		String[] realKeys = parseSpelKeys(joinPoint, cacheEvict.keys());

		scheduler.schedule(() -> {
			String time = format(new Date());
			for (String key : realKeys) {
				log.info("{} 延迟1秒再删一遍缓存: {}", time, key);
				try {
					JedisUtils.del(key);
				} catch (Exception e) {
					log.error("{} 删除缓存失败: {}", format(new Date()), key, e);
				}
			}
		}, 1, SECONDS);
	}

	private CacheEvict getCacheEvictAnnotation(JoinPoint joinPoint) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		return signature.getMethod().getAnnotation(CacheEvict.class);
	}

	/**
	 * 解析注解中定义的 SpEL keys，生成真实的缓存 key
	 * 支持的注解写法示例：
	 *   "category_brands_#pmsCategoryBrandRelationDTO.brandId"
	 *   "user:#user.id:info"
	 *   "prefix_#{#dto.field + '_' + #anotherParam}"
	 */
	private String[] parseSpelKeys(JoinPoint joinPoint, String[] originKeys) {
		if (originKeys == null || originKeys.length == 0) {
			return new String[0];
		}

		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		String[] paramNames = signature.getParameterNames();
		Object[] args = joinPoint.getArgs();

		if (paramNames == null || args == null || paramNames.length != args.length) {
			log.warn("无法获取参数信息，使用原始 key");
			return originKeys;
		}

		// 寻找最可能的“主体”参数（通常是 @RequestBody 的 DTO）
		Map<String, Object> properties = new HashMap<>();
		for (int i = 0; i < args.length; i++) {
			//如果为null，则跳过, 否则下面生成对象的时候会报错 java.lang.IllegalArgumentException: Property values cannot be null; type cannot be inferred.
			if (args[i] != null) {
				properties.put(paramNames[i], args[i]);
			}
		}

		Object rootObject = DynamicUtils.createObject(Object.class, properties);

		String[] resolved = new String[originKeys.length];
		for (int i = 0; i < originKeys.length; i++) {
			String tpl = originKeys[i];
			try {
				String value = SpElUtils.parse(tpl, rootObject);
				resolved[i] = value != null ? value : tpl;
				log.debug("SpEL 解析：{} → {}", tpl, resolved[i]);
			} catch (Exception e) {
				log.error("SpEL 解析失败：{}，原因：{}", tpl, e.getMessage());
				resolved[i] = tpl;
			}
		}

		return resolved;
	}

	// 简单辅助方法：判断是否是基本类型
	private boolean isSimpleType(Class<?> clazz) {
		return clazz.isPrimitive() ||
				Number.class.isAssignableFrom(clazz) ||
				Boolean.class.isAssignableFrom(clazz) ||
				Character.class.isAssignableFrom(clazz) ||
				String.class.isAssignableFrom(clazz) ||
				clazz.isArray() ||
				Collection.class.isAssignableFrom(clazz) ||
				Map.class.isAssignableFrom(clazz);
	}
}