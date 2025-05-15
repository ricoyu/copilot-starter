package com.awesomecopilot.gateway.autoconfig;

import com.awesomecopilot.cloud.gateway.predicate.TimeBetweenRoutePredicateFactory;
import com.awesomecopilot.gateway.properties.CopilotGatewayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基于时间范围的PredicateFactory
 * 时间格式: 7:00,15:33
 *         上午7:00,下午3:00
 * <p>
 * Copyright: (C), 2020/4/23 15:41
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Configuration
@EnableConfigurationProperties(CopilotGatewayProperties.class)
public class CopilotGatewayPredicateAutoConfiguration {
	
	@Bean
	public TimeBetweenRoutePredicateFactory timeBetweenRoutePredicateFactory() {
		return new TimeBetweenRoutePredicateFactory();
	}
	
}
