package com.awesomecopilot.oauth2.handler;

import com.awesomecopilot.common.lang.concurrent.CopilotExecutors;
import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.common.lang.errors.ErrorType;
import com.awesomecopilot.common.lang.errors.ErrorTypes;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.security.constants.CopilotSecurityConstants;
import com.awesomecopilot.security.constants.SpringSecurityExceptions;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.util.concurrent.ThreadPoolExecutor;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
public class LoginFailureHandler implements AuthenticationFailureHandler {
	
	private static final ThreadPoolExecutor POOL = CopilotExecutors.of("login-fail-pool")
			.corePoolSize(1)
			.maxPoolSize(100)
			.queueSize(1000)
			.build();
	
	
	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
	                                    AuthenticationException e) throws IOException, ServletException {
		
		String username = ThreadContext.get(CopilotSecurityConstants.SPRING_SECURITY_FORM_USERNAME_KEY);
		
		boolean processed = false;
		//如果需要在框架外对认证异常消息做处理, 可以实现AuthenticationFailMessageProcessor
		
		//表示只需返回框架提供的默认认证异常消息
			ErrorType errorType = SpringSecurityExceptions.errorType(e.getClass());
			if (errorType == null) {
				if (e.getCause() != null && e.getCause().getClass() == NullPointerException.class) {
					errorType = ErrorTypes.INTERNAL_SERVER_ERROR;
				} else {
					errorType = ErrorTypes.USERNAME_PASSWORD_MISMATCH;
				}
			}
			Result result = Results.status(errorType).build();
			RestUtils.writeJson(response, result);
		
		if (isBlank(username)) {
			return;
		}
	}
	
}
