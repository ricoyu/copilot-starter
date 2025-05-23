package com.awesomecopilot.security6.handler;

import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.common.lang.errors.ErrorType;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.security6.constants.CopilotSecurityConstants;
import com.awesomecopilot.security6.constants.SpringSecurityExceptions;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

import static com.awesomecopilot.common.lang.errors.ErrorTypes.INTERNAL_SERVER_ERROR;

public class LoginFailureHandler implements AuthenticationFailureHandler {

	private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
	                                    AuthenticationException e) throws IOException, ServletException {

		String username = ThreadContext.get(CopilotSecurityConstants.SPRING_SECURITY_FORM_USERNAME_KEY);

		ErrorType errorType = SpringSecurityExceptions.errorType(e.getClass());
		if (errorType == null) {
			//org.springframework.security.authentication.InternalAuthenticationServiceException:
			//org.springframework.security.authentication.BadCredentialsException: 用户名或密码错误
			//LockedException, DisabledException, AccountExpiredException, BadCredentialsException
			/*if (e instanceof BadCredentialsException) {
				errorType = USERNAME_PASSWORD_MISMATCH;
			} else if (e instanceof LockedException) {
				errorType = ACCOUNT_LOCKED;
			} else if (e instanceof DisabledException) {
				errorType = ACCOUNT_DISABLED;
			} else if (e instanceof AccountExpiredException) {
				errorType = ACCOUNT_EXPIRED;
			} else if (e.getCause() != null && e.getCause().getClass() == NullPointerException.class) {
				errorType = INTERNAL_SERVER_ERROR;
			} else {
				errorType = INTERNAL_SERVER_ERROR;
			}*/
			errorType = INTERNAL_SERVER_ERROR;
		}
		Result result = Results.status(errorType).build();
		RestUtils.writeJson(response, result);
	}

}
