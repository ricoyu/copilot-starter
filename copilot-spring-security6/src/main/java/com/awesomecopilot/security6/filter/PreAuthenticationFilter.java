package com.awesomecopilot.security6.filter;

import com.awesomecopilot.cache.auth.AuthUtils;
import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.security6.constants.SecurityConstants;
import com.awesomecopilot.security6.constants.ThreadLocalSecurityConstants;
import com.awesomecopilot.security6.properties.CopilotSecurityProperties;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.util.Map;

import static com.awesomecopilot.common.lang.errors.ErrorTypes.TOKEN_INVALID;
import static com.awesomecopilot.common.lang.errors.ErrorTypes.TOKEN_MISSING;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * 从request中拿token
 * <p>
 * 评审 P2-11: 旧实现在 getPreAuthenticatedPrincipal 里 writeJson(TOKEN_MISSING/TOKEN_INVALID)
 * 之后 return null, 父类 doFilter 仍继续 chain.doFilter——下游可能对同一响应二次写出,
 * 客户端最终看到哪个错误码取决于响应提交顺序. 现在把"需要直接回错误的检查"前置到
 * doFilter: 写完响应立即返回(链路终止); token 无效的仍走原语义(不写响应, 交给
 * AuthorizationFilter 失败后由 RestAuthenticationEntryPoint 统一输出 TOKEN_EXPIRED).
 * <p>
 * 评审 P2-12: 登录路径判定优先使用与 UsernamePasswordAuthenticationFilter 相同的
 * RequestMatcher(starter 装配时注入 AntPathRequestMatcher(loginUrl, POST)), 判定规则集中一处.
 * (2026-09-21 独立评审更正过原表述: AntPathRequestMatcher 6.2.3 取路径同为 servletPath+pathInfo,
 * 与旧的 ServletUtils.requestPath 并非两套解析; 本改动实际收益是规则集中 + 附带 POST 方法限定.)
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
	
	/** doFilter 检查通过后传给 getPreAuthenticatedPrincipal 的请求属性 */
	private static final String PRE_AUTH_PRINCIPAL_ATTR = PreAuthenticationFilter.class.getName() + ".PRINCIPAL";

	@Value("${copilot.security6.user-pass-login.login-url:/login}")
	private String loginUrl;

	/**
	 * White list config, urls in this list do not require authentication
	 */
	private CopilotSecurityProperties properties;
	
	/** 与登录过滤器共用的 RequestMatcher, 可为 null(null 时回退 servletPath 匹配) */
	private RequestMatcher loginRequestMatcher;

	public void setProperties(CopilotSecurityProperties properties) {
		this.properties = properties;
	}
	
	public void setLoginRequestMatcher(RequestMatcher loginRequestMatcher) {
		this.loginRequestMatcher = loginRequestMatcher;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		
		//1. 登录请求与白名单路径: 无需 token, 直接放行(不进入认证流程)
		if (isLoginRequest(request) || isInWhiteList(request)) {
			log.debug("doFilterInternal >> 白名单或登录请求, 跳过认证, path={}", requestPath(request));
			chain.doFilter(request, response);
			return;
		}
		
		//2. token 缺失/格式错误: 写明确错误并终止链路(P2-11, 旧版写完还继续放行)
		String accessToken = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
		if (isBlank(accessToken)) {
			log.warn("doFilter >> 请提供accessToken, path={}", requestPath(request));
			RestUtils.writeJson(response, Results.status(TOKEN_MISSING).build());
			return;
		}
		if (!accessToken.startsWith(SecurityConstants.BEARER_TOKEN_PREFIX)) {
			log.info("doFilter >> token格式无效, path={}", requestPath(request));
			RestUtils.writeJson(response, Results.status(TOKEN_INVALID).build());
			return;
		}
		//去掉Bearer 前缀, 拿到真正的Token
		accessToken = accessToken.substring(SecurityConstants.BEARER_TOKEN_PREFIX.length());
		if (isBlank(accessToken)) {
			log.info("doFilter >> 去除前缀后token为空, path={}", requestPath(request));
			RestUtils.writeJson(response, Results.status(TOKEN_INVALID).build());
			return;
		}
		
		//3. token 有值: 查用户. 查不到不写响应(维持原语义, 交给 EntryPoint 输出 TOKEN_EXPIRED),
		//   查到则写入上下文并进入父类认证流程
		String username = AuthUtils.checkToken(accessToken);
		if (isBlank(username)) {
			log.info("doFilter >> token无效或已过期, path={}", requestPath(request));
			super.doFilter(request, response, chain); //principal 属性未设置 -> 父类取到 null -> 继续 chain
			return;
		}
		log.info("doFilter >> token验证通过, username={}, path={}", username, requestPath(request));
		ThreadContext.put(ThreadLocalSecurityConstants.ACCESS_TOKEN, accessToken); //方便PreAuthenticationUserDetailsService中拿到token
		ThreadContext.put(ThreadLocalSecurityConstants.USERNAME, username);
		@SuppressWarnings("unchecked")
		Map<String, Object> loginInfo = AuthUtils.loginInfo(accessToken, Map.class);
		if (loginInfo != null) {
			if (loginInfo.get(ThreadLocalSecurityConstants.USER_ID) != null) {
				ThreadContext.put(ThreadLocalSecurityConstants.USER_ID, loginInfo.get(ThreadLocalSecurityConstants.USER_ID));
			}
			ThreadContext.put(ThreadLocalSecurityConstants.LOGIN_INFO, loginInfo);
		}
		request.setAttribute(PRE_AUTH_PRINCIPAL_ATTR, username);
		super.doFilter(request, response, chain);
	}
	
	@Override
	protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
		//所有请求级检查已在 doFilter 完成, 这里只取结果
		return request.getAttribute(PRE_AUTH_PRINCIPAL_ATTR);
	}
	
	@Override
	protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
		return "";
	}

	/**
	 * 根据请求的uri和SpringSecurity配置的登录API地址判断是否是登录请求
	 * @return boolean
	 */
	public boolean isLoginRequest(HttpServletRequest request) {
		if (loginRequestMatcher != null) {
			return loginRequestMatcher.matches(request);
		}
		return ANT_PATH_MATCHER.match(loginUrl, requestPath(request));
	}

	/**
	 * Check whether the current request URI matches any pattern in copilot.security6.white-list
	 */
	protected boolean isInWhiteList(HttpServletRequest request) {
		if (properties == null || properties.getWhiteList().isEmpty()) {
			return false;
		}
		String requestPath = requestPath(request);
		for (String pattern : properties.getWhiteList()) {
			if (ANT_PATH_MATCHER.match(pattern, requestPath)) {
				return true;
			}
		}
		return false;
	}
	
	private static String requestPath(HttpServletRequest request) {
		return ServletUtils.requestPath(request);
	}
}
