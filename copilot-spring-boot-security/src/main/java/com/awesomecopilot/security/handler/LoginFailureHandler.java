package com.awesomecopilot.security.handler;

import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.common.lang.concurrent.CopilotExecutors;
import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.common.lang.errors.ErrorType;
import com.awesomecopilot.common.lang.errors.ErrorTypes;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.security.constants.CopilotSecurityConstants;
import com.awesomecopilot.security.constants.SpringSecurityExceptions;
import com.awesomecopilot.security.processor.AuthenticationFailMessageProcessor;
import com.awesomecopilot.security.processor.LoginFailPertimeProcessor;
import com.awesomecopilot.security.service.AccountLockDurationService;
import com.awesomecopilot.security.service.RetryCountService;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.awesomecopilot.security.constants.SecurityConstants.RETRY_COUNT_KEY_PREFIX;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
public class LoginFailureHandler implements AuthenticationFailureHandler {
	
	private static final ThreadPoolExecutor POOL = CopilotExecutors.of("login-fail-pool")
			.corePoolSize(1)
			.maxPoolSize(100)
			.queueSize(1000)
			.build();
	
	@Autowired(required = false)
	private List<AuthenticationFailMessageProcessor> messageProcessors;
	
	@Autowired(required = false)
	private RetryCountService retryCountService;
	
	@Autowired(required = false)
	private AccountLockDurationService accountLockDurationService;
	
	@Autowired(required = false)
	private LoginFailPertimeProcessor loginFailPertimeProcessor;
	
	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
	                                    AuthenticationException e) throws IOException, ServletException {
		
		String username = ThreadContext.get(CopilotSecurityConstants.SPRING_SECURITY_FORM_USERNAME_KEY);
		
		if (loginFailPertimeProcessor != null) {
			POOL.execute(() -> {
				loginFailPertimeProcessor.onLoginFail(username, e);
			});
		}
		
		boolean processed = false;
		//如果需要在框架外对认证异常消息做处理, 可以实现AuthenticationFailMessageProcessor
		if (messageProcessors != null) {
			for (AuthenticationFailMessageProcessor messageProcessor : messageProcessors) {
				if (messageProcessor.supports(e)) {
					String[] messages = messageProcessor.message(e);
					Result result = Results.status(messages[0], messages[1]).build();
					RestUtils.writeJson(response, result);
					processed = true;
					break;
				}
			}
		}
		
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
		
		/*
		 * 5次错误密码后冻结账号5分钟
		 */
		String retryCountKey = RETRY_COUNT_KEY_PREFIX + username;
		
		//失败一次, 计数器+1
		long retryCount = JedisUtils.incr(retryCountKey);
		log.info("失败{}次", retryCount);
		
		/*
		 * 达到失败次数, 设置retryCountKey在lockTimeout分钟后过期, 这样过期时间候RetryCountFilter才能放行
		 */
		int maxRetryCount = 5;
		if (retryCountService != null) {
			maxRetryCount = retryCountService.maxRetryCount(username);
		}
		int lockDuration = 5000;
		if (accountLockDurationService != null) {
			lockDuration = accountLockDurationService.lockTime();
		}
		if (retryCount >= maxRetryCount) {
			JedisUtils.expire(retryCountKey, lockDuration, TimeUnit.MILLISECONDS);
			JedisUtils.publish(RETRY_COUNT_KEY_PREFIX + "channel", username);
		}
	}
	
}
