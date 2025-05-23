package com.awesomecopilot.security6.filter;

import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.json.jsonpath.JsonPathUtils;
import com.awesomecopilot.security6.constants.CopilotSecurityConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * 先尝试表单提交认证、接着是request body认证
 * <p>
 * Copyright: Copyright (c) 2018-07-23 15:44
 * <p>
 * Company: DataSense
 * <p>
 *
 * @author Rico Yu	ricoyu520@gmail.com
 * @version 1.0
 * @on
 */
public class UsernamePasswordAuthenticationFilter extends org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter{

	private static final Logger log = LoggerFactory.getLogger(UsernamePasswordAuthenticationFilter.class);
	private static final String ERROR_MESSAGE = "Something went wrong while parsing /login request body";
	
	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
		Authentication token = null;
		
		// 1 尝试表单提交方式获取用户名密码
		String username = obtainUsername(request);
		String password = obtainPassword(request);
		if (isNotBlank(username) && isNotBlank(password)) {
			token = new UsernamePasswordAuthenticationToken(username, password);
			ThreadContext.put(CopilotSecurityConstants.SPRING_SECURITY_FORM_USERNAME_KEY, username);
		}
		
		if (token != null) {
			return this.getAuthenticationManager().authenticate(token);
		}

		//从request body 中获取用户名密码
		String requestBody = ServletUtils.readRequestBody(request);
		if (isBlank(requestBody)) {
			token = new UsernamePasswordAuthenticationToken("", "");
			return this.getAuthenticationManager().authenticate(token);
		}
		
		username = JsonPathUtils.readNode(requestBody, "$.username");
		password = JsonPathUtils.readNode(requestBody, "$.password");
		if (isNotBlank(username) && isNotBlank(password)) {
			token = new UsernamePasswordAuthenticationToken(username, password);
			ThreadContext.put(CopilotSecurityConstants.SPRING_SECURITY_FORM_USERNAME_KEY, username);
			return this.getAuthenticationManager().authenticate(token);
		}
		
		token = new UsernamePasswordAuthenticationToken("", "");
		return this.getAuthenticationManager().authenticate(token);
	}
}
