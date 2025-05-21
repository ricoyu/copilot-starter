package com.awesomecopilot.security6.handler;

import com.awesomecopilot.cache.auth.AuthUtils;
import com.awesomecopilot.common.lang.concurrent.CopilotExecutors;
import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.common.lang.utils.StringUtils;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.json.jackson.JacksonUtils;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.awesomecopilot.security6.constants.ThreadLocalSecurityConstants.LOGIN_INFO;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * 登录成功后负责生成token
 * <p>
 * Copyright: Copyright (c) 2021-03-30 16:49
 * <p>
 * Company: Information & Data Security Solutions Co., Ltd.
 * <p>
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

	private static final Logger log = getLogger(LoginSuccessHandler.class);
	private static final ThreadPoolExecutor POOL = CopilotExecutors.of("copilot-login-pool").build();
	
	@SuppressWarnings({"unchecked"})
	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
	                                    Authentication authentication) throws IOException, ServletException {
		String accessToken = StringUtils.uniqueKey(66); //这是token
		String username = SecurityContextHolder.getContext().getAuthentication().getName();
		log.info("用户: {} 登录成功, Token: {}", username, accessToken);
		User userDetails = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		List<? extends GrantedAuthority> authorities = (List<? extends GrantedAuthority>) SecurityContextHolder.getContext()
				.getAuthentication()
				.getAuthorities();
		log.info("用户: {} 的权限列表: \n{}", username, JacksonUtils.toPrettyJson(authorities));
		String ip = ServletUtils.getRemoteRealIP(request);
		
		doLogin(response, accessToken, username, userDetails, authorities, ip, false);
	}
	
	private void doLogin(HttpServletResponse response,
	                     String accessToken,
	                     String username,
	                     User userDetails, List<? extends GrantedAuthority> authorities,
	                     String ip,
	                     boolean singleSignOn) {
		Map<String, Object> loginInfo = ThreadContext.get(LOGIN_INFO);
		if (loginInfo == null) {
			loginInfo = new HashMap<>();
			loginInfo.put("ip", ip);
		} else {
			loginInfo.put("ip", ip);
		}

		long expires = 30L;
		AuthUtils.login(username, accessToken, expires, TimeUnit.MINUTES, userDetails, authorities, loginInfo, singleSignOn);
		Result result = Results.success().data(accessToken).build();
		RestUtils.writeJson(response, result);
	}
	
}
