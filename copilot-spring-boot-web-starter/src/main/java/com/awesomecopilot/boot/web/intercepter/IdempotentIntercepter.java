package com.awesomecopilot.boot.web.intercepter;

import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.common.lang.errors.ErrorTypes;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.web.utils.CORS;
import com.awesomecopilot.web.utils.RestUtils;
import com.awesomecopilot.boot.web.annotation.Idempotent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * <p>
 * Copyright: (C), 2022-01-21 16:49
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Slf4j
public class IdempotentIntercepter implements HandlerInterceptor {
	
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if (!(handler instanceof HandlerMethod)) {
			return true;
		}
		HandlerMethod handlerMethod = (HandlerMethod) handler;
		Idempotent idempotent = handlerMethod.getMethodAnnotation(Idempotent.class);
		
		//没有打@Idempotent注解的话不处理
		if (idempotent == null) {
			return true;
		}
		
		String token = request.getHeader("Idempotent-Token");
		if (isBlank(token)) {
			log.warn("请求: {} 未携带Idempotent-Token, 默认拦截掉", request.getContextPath());
			Result result = Results.status(ErrorTypes.MISSING_IDEMPOTENT_TOKEN).build();
			response.setContentType(ServletUtils.APPLICATION_JSON_UTF8);
			CORS.builder().allowAll().build(response);
			RestUtils.writeJson(response, result);
			return false;
		}
		
		Long count = JedisUtils.HASH.hdel("idempotent-token", token);
		if (count == null || count == 0) {
			log.warn("检测到重复提交, 请求URI: {}", request.getContextPath());
			Result result = Results.status(ErrorTypes.DUPLICATE_SUBMISSION).build();
			response.setContentType(ServletUtils.APPLICATION_JSON_UTF8);
			CORS.builder().allowAll().build(response);
			RestUtils.writeJson(response, result);
			return false;
		}
		return true;
	}
}
