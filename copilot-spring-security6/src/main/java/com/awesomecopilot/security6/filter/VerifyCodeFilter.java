package com.awesomecopilot.security6.filter;

import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.common.lang.utils.StringUtils;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.security6.constants.SecurityConstants;
import com.awesomecopilot.security6.errors.SecurityErrors;
import com.awesomecopilot.security6.properties.CopilotSecurityProperties;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * 验证登录时提供的验证码是否正确
 * <p>
 * Copyright: (C), 2020-08-12 15:03
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
public class VerifyCodeFilter extends OncePerRequestFilter {
	
	@Autowired
	private CopilotSecurityProperties properties;
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		HttpServletRequest servletRequest = (HttpServletRequest)request;
		if (!ServletUtils.pathMatch(servletRequest, properties.getUserPassLogin().getLoginUrl())) {
			chain.doFilter(request, response);
			return;
		}
		
		//从request中拿codeId, verifyCode
		String codeId = request.getParameter(SecurityConstants.VERIFY_CODE_ID);
		String verifyCode = request.getParameter(SecurityConstants.VERIFY_CODE);
		
		//万能验证码
		if ("wnyz".equalsIgnoreCase(verifyCode)) {
			chain.doFilter(request, response);
			return;
		}
		
		//没有提供codeId, verifyCode参数
		if (isBlank(codeId) || isBlank(verifyCode)) {
			Result result = Results.status(SecurityErrors.AUTH_CODE_MISS).build();
			RestUtils.writeJson(response, result);
			return;
		}
		
		String code = JedisUtils.get(StringUtils.concat(SecurityConstants.VERIFY_CODE_PREFIX, codeId).toLowerCase());
		//code不匹配或者已经过期
		if (!verifyCode.equalsIgnoreCase(code)) {
			Result result = Results.status(SecurityErrors.AUTH_CODE_EXPIRED).build();
			RestUtils.writeJson(response, result);
			return;
		}
		
		//验证码用过之后删除
		
		chain.doFilter(request, response);
	}
}
