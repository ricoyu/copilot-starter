package com.awesomecopilot.cloud.feign.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * 在feign调用时向下游传递Authorization请求头, 使得下游服务可以完成认证授权
 * <p>
 * Copyright: (C), 2023-03-05 17:07
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
public class AuthorizationInterceptor implements RequestInterceptor {

	private static final Logger log = LoggerFactory.getLogger(AuthorizationInterceptor.class);
	
	@Override
	public void apply(RequestTemplate template) {
		ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		HttpServletRequest request = requestAttributes.getRequest();

		//设置Authorization请求头
		if (!template.headers().containsKey("Authorization")) {
			String Idempotent = UUID.randomUUID().toString().replaceAll("-", "");
			log.info("添加Idempotent请求头: {}", Idempotent);
			template.header("Authorization", Idempotent);
		}
	}
}
