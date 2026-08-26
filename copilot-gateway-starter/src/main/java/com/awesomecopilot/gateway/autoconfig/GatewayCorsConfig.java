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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

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
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowCredentials(true);
		//config.addAllowedOrigin("*");
		config.addAllowedOriginPattern("*");
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
		source.registerCorsConfiguration("/**", config);
		return new CorsWebFilter(source);
	}
}