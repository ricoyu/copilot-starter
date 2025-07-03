package com.awesomecopilot.security6.filter;

import com.awesomecopilot.cache.auth.AuthUtils;
import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.security6.constants.SecurityConstants;
import com.awesomecopilot.security6.constants.ThreadLocalSecurityConstants;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.util.AntPathMatcher;

import java.util.Map;

import static com.awesomecopilot.common.lang.errors.ErrorTypes.TOKEN_INVALID;
import static com.awesomecopilot.common.lang.errors.ErrorTypes.TOKEN_MISSING;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * 从request中拿token
 * <p>
 * Copyright: Copyright (c) 2021-03-30 18:37
 * <p>
 * Company: Information & Data Security Solutions Co., Ltd.
 * <p>
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class PreAuthenticationFilter extends AbstractPreAuthenticatedProcessingFilter {

	private static final Logger log = LoggerFactory.getLogger(PreAuthenticationFilter.class);

	private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

	@Value("${copilot.security6.user-pass-login.login-url:/login}")
	private String loginUrl;

	@Override
	protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
		return "";
	}
	
	@Override
	protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
		String accessToken = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
		HttpServletResponse response = ServletUtils.response();
		if (isLoginRequest()) {
			return null;
		}
		if (isBlank(accessToken)) {
			log.warn("请提供accessToken");
			Result result = Results.status(TOKEN_MISSING).build();
			RestUtils.writeJson(response, result);
			return null;
		}

		boolean startsWith = accessToken.startsWith(SecurityConstants.BEARER_TOKEN_PREFIX);
		if (!startsWith) {
			Result result = Results.status(TOKEN_INVALID).build();
			RestUtils.writeJson(response, result);
			return null;
		}
		//去掉Bearer 前缀, 拿到真正的Token
		accessToken = accessToken.replaceAll(SecurityConstants.BEARER_TOKEN_PREFIX, "");
		
		if (isBlank(accessToken)) {
			log.info("Access token 为空");
			return null;
		}
		
		/**
		 * 根据token找对应的username
		 */
		String username = AuthUtils.auth(accessToken);
		if (isNotBlank(username)) {
			ThreadContext.put(ThreadLocalSecurityConstants.ACCESS_TOKEN, accessToken); //方便PreAuthenticationUserDetailsService中拿到token
			ThreadContext.put(ThreadLocalSecurityConstants.USERNAME, username);

			Map<String, Object> loginInfo = AuthUtils.loginInfo(accessToken, Map.class);
			if (loginInfo != null) {
				if (loginInfo.get(ThreadLocalSecurityConstants.USER_ID) != null) {
					ThreadContext.put(ThreadLocalSecurityConstants.USER_ID, loginInfo.get(ThreadLocalSecurityConstants.USER_ID));
				}
				ThreadContext.put(ThreadLocalSecurityConstants.LOGIN_INFO, loginInfo);
			}
			return username;
		}
		
		return null;
	}

	/**
	 * 根据请求的uri和SpringSecurity配置的登录API地址判断是否是登录请求
	 * @return boolean
	 */
	public boolean isLoginRequest() {
		String requestPath = ServletUtils.requestPath();
		return ANT_PATH_MATCHER.match(loginUrl, requestPath);
	}
}
