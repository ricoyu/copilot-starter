package com.awesomecopilot.cloud.autoconfig;

import com.awesomecopilot.cloud.gateway.predicate.TimeBetweenRoutePredicateFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
	@ConditionalOnProperty(prefix = "copilot.gateway", value = "time-between-route-enabled", havingValue = "true", matchIfMissing = true)
	public TimeBetweenRoutePredicateFactory timeBetweenRoutePredicateFactory() {
		return new TimeBetweenRoutePredicateFactory();
	}
	
}
