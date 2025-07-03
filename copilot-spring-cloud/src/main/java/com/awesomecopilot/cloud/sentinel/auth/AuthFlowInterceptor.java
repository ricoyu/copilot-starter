package com.awesomecopilot.cloud.sentinel.auth;

import com.awesomecopilot.cloud.properties.SentinelProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * 支持Sentinel授权规则流控, 需要在调用方为feign配置这个拦截器
 * <p>
 * Copyright: Copyright (c) 2023-03-22 11:11
 * <p>
 * Company: Sexy Uncle Inc.
 * <p>
 
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class AuthFlowInterceptor implements RequestInterceptor {

	private static final Logger log = LoggerFactory.getLogger(AuthFlowInterceptor.class);

	@Autowired
	private SentinelProperties sentinelProperties;

	@Override
	public void apply(RequestTemplate template) {
		ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		HttpServletRequest request = requestAttributes.getRequest();
		// 获取请求头Origin(默认值)
		String header = sentinelProperties.getAuthRule().getHeader();
		String headerValue = request.getHeader(header);

		//设置Idempotent请求头
		if (!template.headers().containsKey(header)) {
			if (isNotBlank(headerValue)) {
				log.info("添加{}请求头: {}", header, headerValue);
				template.header(header, headerValue);
			}
		}
	}

}
