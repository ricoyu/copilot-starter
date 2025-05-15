package com.awesomecopilot.gateway.sentinel.autoconfig;

import com.awesomecopilot.gateway.sentinel.handler.GatewayBlockRequestHandler;
import com.awesomecopilot.gateway.sentinel.properties.CopilotGatewaySentinelProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * Copyright: (C), 2020/5/1 14:56
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Configuration
@EnableConfigurationProperties(CopilotGatewaySentinelProperties.class)
public class CopilotGatewaySentinelAutoConfig {
	
	@Bean
	@ConditionalOnProperty(name = "copilot.gateway.sentinel.enabled", havingValue = "true", matchIfMissing = false)
	public GatewayBlockRequestHandler gatewayBlockRequestHandler() {
		return new GatewayBlockRequestHandler();
	}
}
