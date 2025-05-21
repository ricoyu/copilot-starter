package com.awesomecopilot.security6.handler;

import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.common.lang.errors.ErrorType;
import com.awesomecopilot.common.lang.errors.ErrorTypes;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.security6.constants.CopilotSecurityConstants;
import com.awesomecopilot.security6.constants.SpringSecurityExceptions;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
public class LoginFailureHandler implements AuthenticationFailureHandler {
	
	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
	                                    AuthenticationException e) throws IOException, ServletException {
		
		String username = ThreadContext.get(CopilotSecurityConstants.SPRING_SECURITY_FORM_USERNAME_KEY);
		
		boolean processed = false;

		//表示只需返回框架提供的默认认证异常消息
		if (!processed) {
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
		}

		if (isBlank(username)) {
			return;
		}
	}
	
}
