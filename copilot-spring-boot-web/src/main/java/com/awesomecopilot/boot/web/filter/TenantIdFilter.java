package com.awesomecopilot.boot.web.filter;

import com.awesomecopilot.common.lang.context.ThreadContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 负责从请求头中获取 Tenant-Id 并存入 ThreadLocal
 * <p/>
 * Copyright: Copyright (c) 2025-04-08 12:56
 * <p/>
 * Company: Sexy Uncle Inc.
 * <p/>

 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class TenantIdFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(TenantIdFilter.class);

	@Override
	protected void doFilterInternal(HttpServletRequest request,
	                                HttpServletResponse response,
	                                FilterChain filterChain)
			throws ServletException, IOException {

		// 从请求头获取 Tenant-Id
		String tenantId = request.getHeader("Tenant-Id");
		// 将 Tenant-Id 存入 ThreadLocal
		ThreadContext.put("tenantId", Long.parseLong(tenantId));
		if (log.isDebugEnabled()) {
			log.debug("Tenant-Id: {}", tenantId);
		}


		// 继续执行后续过滤器/Controller
		filterChain.doFilter(request, response);
	}
}