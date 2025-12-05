package com.awesomecopilot.gateway.autoconfig;

import com.awesomecopilot.cloud.gateway.auth.properties.CopilotGatewayProperties;
import com.awesomecopilot.cloud.gateway.filter.TimeMonitorGatewayFilterFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * <p>
 * Copyright: (C), 2020/4/24 11:05
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Configuration
@EnableConfigurationProperties(CopilotGatewayProperties.class)
public class CopilotGatewayFilterAutoConfiguration {
	
	@Bean
	@ConditionalOnProperty(prefix = "copilot.gateway", value = "time-monitor-filter-enabled", havingValue = "true", matchIfMissing = true)
	public TimeMonitorGatewayFilterFactory timeMonitorGatewayFilterFactory() {
		return new TimeMonitorGatewayFilterFactory();
	}
}
