package com.awesomecopilot.boot.security.filter;

import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.common.lang.errors.ErrorTypes;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.web.utils.RestUtils;
import com.awesomecopilot.boot.security.errors.SecurityErrors;
import com.awesomecopilot.boot.security.processor.RetryCountMessageProcessor;
import com.awesomecopilot.boot.security.props.CopilotSecurityProperties;
import com.awesomecopilot.boot.security.service.RetryCountService;
import com.awesomecopilot.security.constants.CopilotSecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.awesomecopilot.boot.security.constants.SecurityConstants.RETRY_COUNT_KEY_PREFIX;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
public class RetryCountFilter extends OncePerRequestFilter {
	
	@Autowired
	private CopilotSecurityProperties properties;
	
	@Autowired(required = false)
	private RetryCountMessageProcessor retryCountMessageProcessor;
	
	@Autowired(required = false)
	private RetryCountService retryCountService;
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		//只有调用登录接口才需要执行过滤
		if (!ServletUtils.pathMatch(request, properties.getLoginUrl())) {
			chain.doFilter(request, response);
			return;
		}
		
		String username = request.getParameter(CopilotSecurityConstants.SPRING_SECURITY_FORM_USERNAME_KEY);
		if (isBlank(username)) {
			log.info("没有输入用户名 {}", username);
			RestUtils.writeJson(response, Results.status(ErrorTypes.USERNAME_PASSWORD_MISMATCH).build());
			return;
		}
		
		/*
		 * 5次错误密码后冻结账号5分钟
		 */
		String retryCountKey = RETRY_COUNT_KEY_PREFIX + username;
		Long retryCount = JedisUtils.getLong(retryCountKey);
		if (retryCount == null) {
			chain.doFilter(request, response);
			return;
		}
		
		int maxRetryCount = properties.getMaxRetries();
		if (retryCountService != null) {
			maxRetryCount = retryCountService.maxRetryCount(username);
		}
		
		log.info("Retry count [{}]", retryCount);
		if (retryCount != null && retryCount >= maxRetryCount) {
			log.info("超过登录失败次数限制 {}", retryCount);
			if (retryCountMessageProcessor != null) {
				//如果系统可以动态配置失败多少次冻结几分钟, 那么错误消息也要动态构建
				String message = retryCountMessageProcessor.retryCountExceeded(username);
				RestUtils.writeJson(response, Results.status(SecurityErrors.RETRY_COUNT_EXCEED.code(), message).build());
			} else {
				RestUtils.writeJson(response, Results.status(SecurityErrors.RETRY_COUNT_EXCEED).build());
			}
			return;
		}
		
		chain.doFilter(request, response);
	}
}
