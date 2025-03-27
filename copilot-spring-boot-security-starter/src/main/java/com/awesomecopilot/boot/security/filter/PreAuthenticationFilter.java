package com.awesomecopilot.boot.security.filter;

import com.awesomecopilot.cache.auth.AuthUtils;
import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.security.vo.AuthRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import java.util.Map;

import static com.awesomecopilot.boot.security.constants.ThreadLocalSecurityConstants.ACCESS_TOKEN;
import static com.awesomecopilot.boot.security.constants.ThreadLocalSecurityConstants.AUTH_REQUEST;
import static com.awesomecopilot.boot.security.constants.ThreadLocalSecurityConstants.LOGIN_INFO;
import static com.awesomecopilot.boot.security.constants.ThreadLocalSecurityConstants.USERNAME;
import static com.awesomecopilot.boot.security.constants.ThreadLocalSecurityConstants.USER_ID;
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
@Slf4j
public class PreAuthenticationFilter extends AbstractPreAuthenticatedProcessingFilter {
	
	@Override
	protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
		return "";
	}
	
	@Override
	protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
		AuthRequest authRequest = ThreadContext.get(AUTH_REQUEST);
		String accessToken = authRequest != null ? authRequest.getAccessToken() : null;
		
		if (isBlank(accessToken)) {
			log.info("Access token 为空");
			return null;
		}
		
		/**
		 * 根据token找对应的username
		 */
		String username = AuthUtils.auth(accessToken);
		if (isNotBlank(username)) {
			ThreadContext.put(ACCESS_TOKEN, accessToken); //方便PreAuthenticationUserDetailsService中拿到token
			ThreadContext.put(USERNAME, username);
			
			Map<String, Object> loginInfo = AuthUtils.loginInfo(accessToken, Map.class);
			if (loginInfo != null) {
				if (loginInfo.get(USER_ID) != null) {
					ThreadContext.put(USER_ID, loginInfo.get(USER_ID));
				}
				ThreadContext.put(LOGIN_INFO, loginInfo);
			}
			return username;
		}
		
		return null;
	}
}
