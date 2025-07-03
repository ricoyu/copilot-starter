package com.awesomecopilot.boot.web.filter;

import com.awesomecopilot.boot.web.autoconfig.CopilotFilterProperties;
import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.common.lang.errors.ErrorTypes;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.common.spring.context.ApplicationContextHolder;
import com.awesomecopilot.web.utils.RestUtils;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * 负责从请求头中获取 Tenant-Id 并存入 ThreadLocal
 * <p/>
 * Copyright: Copyright (c) 2025-04-08 12:56
 * <p/>
 * Company: Sexy Uncle Inc.
 * <p/>
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class TenantIdFilter extends OncePerRequestFilter {

	public static final String TENANT_ID_HEADER = "Tenant-Id";

	private static final Logger log = LoggerFactory.getLogger(TenantIdFilter.class);

	private boolean tenantIdMandatory = false;

	private CopilotFilterProperties properties;

	public TenantIdFilter(boolean tenantIdMandatory) {
		this.tenantIdMandatory = tenantIdMandatory;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
	                                HttpServletResponse response,
	                                FilterChain filterChain)
			throws ServletException, IOException {

		// 从请求头获取 Tenant-Id
		String tenantId = request.getHeader(TENANT_ID_HEADER);
		if (isBlank(tenantId) && tenantIdMandatory) {
			if (properties == null) {
				properties = (CopilotFilterProperties) ApplicationContextHolder.getBean(CopilotFilterProperties.class);
			}

			HttpServletRequest httpRequest = (HttpServletRequest) request;
			String requestURI = httpRequest.getRequestURI();

			List<String> excludeUrls = new ArrayList<>();
			if (properties.getTenant() != null) {
				excludeUrls = properties.getTenant().getExcludeUrls();
			}
			if (!shouldExclude(requestURI, excludeUrls)) {
				Result<String> result = Results.<String>status(ErrorTypes.MISSING_TENANT_ID).build();
				RestUtils.writeJson(response, result);
				return;
			}
		}
		// 将 Tenant-Id 存入 ThreadLocal
		ThreadContext.put("tenantId", isBlank(tenantId) ? null : Long.parseLong(tenantId.trim()));
		if (log.isDebugEnabled()) {
			log.debug("Tenant-Id: {}", tenantId);
		}


		// 继续执行后续过滤器/Controller
		filterChain.doFilter(request, response);
	}

	/**
	 * 检查请求URI是否应该被排除
	 */
	private boolean shouldExclude(String requestURI, List<String> excludeUrls) {
		return excludeUrls.stream().anyMatch(excludeUrl ->
				pathMatches(requestURI, excludeUrl)
		);
	}

	/**
	 * 简单的路径匹配方法，支持 * 通配符
	 */
	private boolean pathMatches(String path, String pattern) {
		if (!pattern.startsWith("/")) {
			pattern = "/" + pattern;
		}
		if (pattern.endsWith("**")) {
			String basePattern = pattern.substring(0, pattern.length() - 2);
			return path.startsWith(basePattern);
		} else if (pattern.endsWith("*")) {
			String basePattern = pattern.substring(0, pattern.length() - 2);
			return path.startsWith(basePattern);
		}
		return path.equalsIgnoreCase(pattern);
	}
}