package com.awesomecopilot.security6.filter;

import com.awesomecopilot.security6.utils.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每个请求结束时清理当前线程的用户上下文(USER_ID/USERNAME/ACCESS_TOKEN/LOGIN_INFO).
 * <p>
 * 评审 P2-10: UserContextHolder.clear() 此前没有任何调用点, 清理依赖可选引入的
 * web-starter ThreadLocalCleanupListener(且只覆盖 Tomcat 请求线程)——业务线程池
 * (含本模块 CopilotExecutors)复用线程时, 上一用户的上下文残留并被带进下一个任务.
 * security6 自带本过滤器后不再依赖外部组件: 请求线程无论正常完成还是链中抛异常都清理.
 * <p>
 * Copyright: (C), 2026-09-21
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
public class SecurityContextCleanupFilter extends OncePerRequestFilter {
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
	                                FilterChain filterChain) throws ServletException, IOException {
		try {
			filterChain.doFilter(request, response);
		} finally {
			//无论链是否抛异常都清理; clear() 只 remove ThreadLocal 条目, 本身不抛
			UserContextHolder.clear();
		}
	}
}
