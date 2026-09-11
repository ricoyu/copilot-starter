package com.awesomecopilot.cloud.gateway.auth.filter;

import com.awesomecopilot.cache.auth.AuthUtils;
import com.awesomecopilot.cloud.gateway.auth.properties.CopilotGatewayProperties;
import com.awesomecopilot.cloud.gateway.exception.GatewayException;
import com.awesomecopilot.common.lang.errors.ErrorTypes;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@EnableConfigurationProperties(value= {CopilotGatewayProperties.class, org.springframework.cloud.gateway.config.GatewayProperties.class})
public class AuthenticationFilter implements GlobalFilter, Ordered {
	
	@Autowired
	private CopilotGatewayProperties gatewayAuthProperties;
	private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();
	
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String requestPath = exchange.getRequest().getURI().getPath();
		log.info("网关开始认证url: {}", requestPath);
		
		if (isInWhiteList(requestPath)) {
			log.info("无需认证的路径: {}", requestPath);
			return chain.filter(exchange);
		}
		
		//获取请求头
		String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
		if (StringUtils.isEmpty(authorization)) {
			log.warn("请求的url需要认证, 但是Authorization为空");
			throw new GatewayException(ErrorTypes.MISSING_AUTHORIZATION);
		}
		
		if (!authorization.startsWith("Bearer ")) {
			log.warn("Authorization头格式不正确, 应以Bearer 开头");
			throw new GatewayException(ErrorTypes.TOKEN_INVALID);
		}
		
		//去掉Bearer 前缀, 拿到真正的Token
		String accessToken = StringUtils.substringAfter(authorization, "Bearer ");
		
		String username = null;
		try {
			// 从Redis验证token, 返回username表示有效, null表示无效/过期
			username = AuthUtils.checkToken(accessToken);
		} catch (Exception e) {
			log.error("校验令牌异常:{}", e);
			throw new GatewayException(ErrorTypes.TOKEN_INVALID);
		}
		
		if (StringUtils.isEmpty(username)) {
			log.warn("token无效或已过期");
			throw new GatewayException(ErrorTypes.TOKEN_INVALID);
		}
		
		log.info("Token验证通过, 用户: {}", username);
		
		ServerHttpRequest request = exchange.getRequest().mutate().header("username", username).build();
		//将现在的request 变成 change对象
		ServerWebExchange serverWebExchange = exchange.mutate().request(request).build();
		
		return chain.filter(exchange);
		
	}
	
	public boolean isInWhiteList(String requestPath) {
		for (String shouldSkipUrl : gatewayAuthProperties.getAuth().getWhiteList()) {
			if (ANT_PATH_MATCHER.match(shouldSkipUrl, requestPath)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public int getOrder() {
		return 0;
	}
}
