package com.awesomecopilot.gateway.autoconfig;

import com.awesomecopilot.cloud.gateway.client.CopilotRestTemplate;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * Copyright: (C), 2020/4/25 15:04
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Configuration
public class CopilotGatewayAutoConfiguration {

	/**
	 * 支持服务一起动就可以通过服务名调用的版本
	 * @param discoveryClient
	 * @return CopilotRestTemplate
	 */
	@Bean
	@LoadBalanced
	public CopilotRestTemplate copilotRestTemplate(DiscoveryClient discoveryClient) {
		return new CopilotRestTemplate(discoveryClient);
	}
}
