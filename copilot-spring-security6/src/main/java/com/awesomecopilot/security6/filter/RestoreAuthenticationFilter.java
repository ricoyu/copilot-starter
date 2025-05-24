package com.awesomecopilot.security6.filter;

import com.awesomecopilot.cache.auth.AuthUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.awesomecopilot.security6.constants.SecurityConstants.AUTHORIZATION_HEADER;
import static com.awesomecopilot.security6.constants.SecurityConstants.BEARER_TOKEN_PREFIX;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class RestoreAuthenticationFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String accessToken = request.getHeader(AUTHORIZATION_HEADER);

		if (isBlank(accessToken)) {
			filterChain.doFilter(request, response);
			return;
		}

		boolean startsWith = accessToken.startsWith(BEARER_TOKEN_PREFIX);
		if (startsWith) {
			//去掉Bearer 前缀, 拿到真正的Token
			accessToken = accessToken.replaceAll(BEARER_TOKEN_PREFIX, "");
			UsernamePasswordAuthenticationToken authentication =
					AuthUtils.getAuthentication(accessToken, UsernamePasswordAuthenticationToken.class);
			/*
			 * SecurityContextHolder.getContext()实际是从SecurityContextHolderStrategy中取出来的,
			 * 而这个SecurityContextHolderStrategy默认的实现是基于ThreadLocal的, 但是在多线程的情况下
			 * 为了ThreadLocal不会产生内存泄漏, copilot-spring-boot-web-starter里面注册了一个ServletRequestListener,
			 * 每次请求进来以及处理完成之后清理一遍ThreadLocal, 所以登录时候往context里面塞的authentication就拿不到了,
			 * 而且即便没有清理, 后续通过携带token访问的时候tomcat的线程也不一定和登录的时候是同一个tomcat线程, 这也会导致
			 * SecurityContextHolder.getContext().getAuthentication()拿不到authentication, 所以在登录成功的时候将token与
			 * authentication关联起来放到了Redis的哈希token:authentication里面关联起来
			 */
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		filterChain.doFilter(request, response);
	}
}
