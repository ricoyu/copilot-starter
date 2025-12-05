package com.awesomecopilot.gateway.autoconfig;

import com.awesomecopilot.cloud.gateway.auth.properties.CopilotGatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsProcessor;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.DefaultCorsProcessor;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关全局跨域配置（Java Config 方式）
 */
@Configuration
@EnableConfigurationProperties(CopilotGatewayProperties.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayCorsConfig {

	private static Logger log = LoggerFactory.getLogger(GatewayCorsConfig.class);

	@Autowired
	private CopilotGatewayProperties copilotGatewayProperties;

	@Bean
	@ConditionalOnProperty(prefix = "copilot.gateway.cors", name = "enabled", havingValue = "true", matchIfMissing =false)
	public CorsWebFilter corsWebFilter() {
		CopilotGatewayProperties.CORS cors = copilotGatewayProperties.getCors();
		// 1. 构建跨域配置对象
		CorsConfiguration config = new CorsConfiguration();

		// 允许的跨域源：生产环境建议替换为具体域名，如 "https://admin.xxx.com"
		// 注意：allowCredentials为true时，不能使用*，必须指定具体域名
		if (cors.getAllowedOrigins().isEmpty()) {
			if (cors.isAllowCredentials() && "*".equals(cors.getAllowedOriginPattern())) {
				String errorMsg = String.format(
						"跨域配置错误：allowCredentials=true 时，allowedOriginPatterns 不能设为 '*'（当前值：%s），请配置具体域名/通配符（如 " +
								"https://*" +
								".xxx.com）",
						cors.getAllowedOriginPattern()
				);
				throw new IllegalArgumentException(errorMsg);
			} else {
				config.addAllowedOriginPattern(cors.getAllowedOriginPattern());
			}
		} else {
			for (String allowedOrigin : cors.getAllowedOrigins()) {
				if (cors.isAllowCredentials() && "*".equals(allowedOrigin)) {
					String errorMsg = String.format(
							"跨域配置错误：allowCredentials=true 时，allowedOrigins 不能包含 '*'（当前非法值：%s），请配置具体域名",
							allowedOrigin
					);
					throw new IllegalArgumentException(errorMsg);
				} else {
					config.addAllowedOrigin(allowedOrigin);
				}
			}
		}

		// 允许的请求头：* 表示所有头
		config.setAllowedHeaders(cors.getAllowedHeaders());

		// 允许的请求方法：* 表示所有方法（GET/POST/PUT/DELETE等）
		config.setAllowedMethods(cors.getAllowedMethods());

		// 是否允许携带 Cookie（前后端需一致）
		config.setAllowCredentials(cors.isAllowCredentials());

		// 预检请求（OPTIONS）的缓存时间，单位秒，减少预检次数
		config.setMaxAge(cors.getMaxAge());

		// 2. 配置跨域规则的匹配路径（/** 表示所有路径）
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);

		// 3. 自定义CorsProcessor，处理前清理重复的CORS头
		CorsProcessor corsProcessor = new DefaultCorsProcessor() {
			@Override
			public boolean process(CorsConfiguration config, ServerWebExchange exchange) {
				HttpHeaders resHeaders = exchange.getResponse().getHeaders();
				// 核心：如果已存在CORS头, 直接返回（避免重复添加）
				if (resHeaders.containsKey("Access-Control-Allow-Origin")) {
					return true;
				}
				// 关键：先移除响应中已有的Access-Control-Allow-Origin头（微服务漏传的情况）
				exchange.getResponse().getHeaders().remove("Access-Control-Allow-Origin");
				exchange.getResponse().getHeaders().remove("Access-Control-Allow-Credentials");
				exchange.getResponse().getHeaders().remove("Access-Control-Allow-Methods");
				exchange.getResponse().getHeaders().remove("Access-Control-Allow-Headers");
				// 执行原生的CORS处理逻辑
				return super.process(config, exchange);
			}
		};

		// 4. 构建自定义CorsWebFilter（贴合源码构造方法）
		return new CorsWebFilter(source, corsProcessor) {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, org.springframework.web.server.WebFilterChain chain) {
				// 双重保障：过滤前再检查一次响应头，避免重复
				exchange.getResponse().getHeaders().remove("Access-Control-Allow-Origin");
				exchange.getResponse().getHeaders().remove("Access-Control-Allow-Credentials");
				exchange.getResponse().getHeaders().remove("Access-Control-Allow-Methods");
				exchange.getResponse().getHeaders().remove("Access-Control-Allow-Headers");
				// 执行原生filter逻辑
				return super.filter(exchange, chain);
			}
		};
	}
}