package com.awesomecopilot.boot.rocketmq5x.autoconfig;

import com.awesomecopilot.boot.rocketmq5x.aspect.RocketMQ5xIdempotentAspect;
import com.awesomecopilot.boot.rocketmq5x.properties.CopilotRocketMQ5xProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * Copyright: (C), 2022-10-25 8:23
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Configuration
@EnableConfigurationProperties(CopilotRocketMQ5xProperties.class)
public class CopilotRocketMQ5xAutoConfig {
	
	@Bean
	@ConditionalOnProperty(value = "copilot.rocket5x.idempotent.enabled", matchIfMissing = false)
	public RocketMQ5xIdempotentAspect rocketMQ5xIdempotentAspect(CopilotRocketMQ5xProperties properties) {
		CopilotRocketMQ5xProperties.Idempotent idempotent = properties.getIdempotent();
		return new RocketMQ5xIdempotentAspect(idempotent.getTtlSeconds(), idempotent.isFailOpenOnRedisError());
	}
}