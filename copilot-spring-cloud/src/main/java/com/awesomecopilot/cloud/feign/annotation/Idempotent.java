package com.awesomecopilot.cloud.feign.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要做接口幂等性的Controller方法上可以加上这个注解 <br/>
 * 调用方的feign要加上这个拦截器 com.awesomecopilot.cloud.feign.interceptor.IdempotentInterceptor 以填充Idempotent请求头到Header中 <br/>
 * 处理@Idempotent注解的是IdempotentAspect <br/>
 * 加了@Idempotent注解的微服务要在application.yaml中配置 copilot.sentinel.enabled: true, 否则不会生效
 * <p>
 * Copyright: (C), 2023-03-06 9:46
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {
}
