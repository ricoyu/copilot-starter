package com.awesomecopilot.cloud.origin;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.RequestOriginParser;
import com.awesomecopilot.cloud.properties.SentinelProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 对授权规则的支持以及流控那边根据调用方限流支持
 * <p/>
 * Copyright: Copyright (c) 2025-05-07 15:42
 * <p/>
 * Company: Sexy Uncle Inc.
 * <p/>
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class CopilotOriginParser implements RequestOriginParser {

	private static final Logger log = LoggerFactory.getLogger(CopilotOriginParser.class);

	@Autowired
	private SentinelProperties sentinelProperties;

	@Override
	public String parseOrigin(HttpServletRequest request) {
		String header = sentinelProperties.getAuthRule().getHeader();
		String headerValue = request.getHeader(header);
		log.info("请求头 {} 的值为 {}", header, headerValue);
		return headerValue;
	}
}