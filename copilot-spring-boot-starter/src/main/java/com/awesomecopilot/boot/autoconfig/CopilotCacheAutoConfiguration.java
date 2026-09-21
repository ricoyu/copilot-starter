package com.awesomecopilot.boot.autoconfig;

import com.awesomecopilot.boot.annotation.processor.RedisListenerProcessor;
import com.awesomecopilot.boot.aspect.CopilotCacheEvictAspect;
import com.awesomecopilot.boot.autoconfig.properties.CopilotCacheProperties;
import com.awesomecopilot.cache.JedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
@ConditionalOnClass(JedisUtils.class)
@ConditionalOnProperty(prefix = "copilot.cache", value = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({CopilotCacheProperties.class})
public class CopilotCacheAutoConfiguration {

	private Logger log = LoggerFactory.getLogger(CopilotCacheAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean(RedisListenerProcessor.class)
	public RedisListenerProcessor redisListenerProcessor() {
		return new RedisListenerProcessor();
	}

	@Bean
	@ConditionalOnMissingBean(CopilotCacheEvictAspect.class)
	public CopilotCacheEvictAspect cacheAspect(CopilotCacheProperties copilotCacheProperties) {
		return new CopilotCacheEvictAspect(copilotCacheProperties.getEvictDelaySeconds(),
			copilotCacheProperties.getEvictMaxPendingTasks());
	}
}