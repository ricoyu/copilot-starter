package com.awesomecopilot.security6.intercepter;

import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.common.lang.errors.ErrorTypes;
import com.awesomecopilot.common.lang.utils.StringUtils;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.security6.annotation.AntiDupSubmit;
import com.awesomecopilot.web.utils.CORS;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.lang.reflect.Method;

import static com.awesomecopilot.security6.constants.SecurityConstants.AUTHORIZATION_HEADER;
import static com.awesomecopilot.security6.constants.SecurityConstants.BEARER_TOKEN_PREFIX;
import static java.text.MessageFormat.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * 根据token以及所请求的方法，限定一定时间内不可重复提交
 * 
 * Copyright: Copyright (c) 2017-09-28 16:09
 * <p>
 * Company: DataSense
 * <p>
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 * @on
 */
public class TokenBasedAntiDupSubmitIntercepter implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(TokenBasedAntiDupSubmitIntercepter.class);

	private static final Logger logger = LoggerFactory.getLogger(TokenBasedAntiDupSubmitIntercepter.class);
	private static final String TOKEN_ANTI_SUBMIT_KEY_TEMPLATE = "anti:dup:submit:{0}:{1}";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if (!(handler instanceof HandlerMethod)) {
			return true;
		}

		HandlerMethod handlerMethod = (HandlerMethod) handler;
		Method method = handlerMethod.getMethod();
		AntiDupSubmit antiDupSubmit = method.getAnnotation(AntiDupSubmit.class);
		
		if (antiDupSubmit != null) {
			long timeout = antiDupSubmit.value();
			String accessToken = getToken(request);
			if (isBlank(accessToken)) {
				return false;
			}
			
			String fullMethodName = StringUtils.concat(method.getDeclaringClass().getName(), ".", method.getName());
			String key = format(TOKEN_ANTI_SUBMIT_KEY_TEMPLATE, fullMethodName, accessToken);
			boolean success = JedisUtils.setnx(key, "", timeout, MILLISECONDS);

			if (!success) {
				logger.info("捕捉到重复提交了:{}", fullMethodName);
				Result result = Results.status(ErrorTypes.DUPLICATE_SUBMISSION).build();
				response.setContentType(ServletUtils.APPLICATION_JSON_UTF8);
				CORS.builder().allowAll().build(response);
				RestUtils.writeJson(response, result);
				return false;
			}

		}

		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {

	}

	private String getToken(HttpServletRequest request) {
		String accessToken = request.getHeader(AUTHORIZATION_HEADER);
		
		if (isBlank(accessToken)) {
			return null;
		}
		
		boolean startsWith = accessToken.startsWith(BEARER_TOKEN_PREFIX);
		if (!startsWith) {
			return null;
		}
		//去掉Bearer 前缀, 拿到真正的Token
		return accessToken.replaceAll(BEARER_TOKEN_PREFIX, "");
	}

}
