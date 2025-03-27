package com.awesomecopilot.boot.web.autoconfig;

import com.awesomecopilot.boot.annotation.processor.RedisListenerProcessor;
import com.awesomecopilot.boot.web.autoconfig.properties.CopilotCacheProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * Copyright: (C), 2020-09-10 14:30
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Configuration
@ConditionalOnProperty(prefix = "copilot.cache", value = "enabled", havingValue = "true")
@EnableConfigurationProperties({CopilotCacheProperties.class})
public class CopilotCacheAutoConfiguration {
	
	@Bean
	public RedisListenerProcessor redisListenerProcessor() {
		return new RedisListenerProcessor();
	}
}
