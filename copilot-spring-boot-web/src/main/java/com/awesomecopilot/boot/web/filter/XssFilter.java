package com.awesomecopilot.boot.web.filter;

import com.awesomecopilot.web.http.XssHttpServletRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * XSS过滤防护
 * <p/>
 * 会把url参数, 表单参数和请求头参数的一些危险HTML标签删掉而不是对所有HTML标签做转义
 * <p/>
 * 比如输入: "<SCRIPT>alert('XSS')</SCRIPT><p>正常内容</p><img src=x ONERROR=alert(1)>"
 * <p/>
 * 那么转义后后端拿到的参数值是: <p>正常内容</p><img src=x >
 * Copyright: Copyright (c) 2025-12-24 21:53
 * <p/>
 * Company: Sexy Uncle Inc.
 * <p/>

 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class XssFilter  extends OncePerRequestFilter {
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		// 包装原始请求，重写参数获取方法
		HttpServletRequestWrapper xssRequestWrapper = new XssHttpServletRequestWrapper((HttpServletRequest) request);
		chain.doFilter(xssRequestWrapper, response);
	}
}
