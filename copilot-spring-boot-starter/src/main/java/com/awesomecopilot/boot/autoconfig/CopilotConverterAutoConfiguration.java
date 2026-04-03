package com.awesomecopilot.boot.autoconfig;

import com.awesomecopilot.boot.converter.LocalTimeConverter;
import com.awesomecopilot.boot.autoconfig.properties.CopilotJacksonProperties;
import com.awesomecopilot.boot.autoconfig.properties.CopilotOrmProperties;
import com.awesomecopilot.boot.autoconfig.properties.CopilotOrmSnowflakeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * Copyright: (C), 2020/4/23 15:05
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Configuration
@EnableConfigurationProperties({CopilotOrmSnowflakeProperties.class, CopilotOrmProperties.class, CopilotJacksonProperties.class})
public class CopilotConverterAutoConfiguration {
	
	/**
	 * properties或者yml中时间转LocalTime支持
	 *
	 * @return LocalTimeConverter
	 */
	@Bean
	@ConditionalOnMissingBean(LocalTimeConverter.class)
	public LocalTimeConverter localTimeConverter() {
		return new LocalTimeConverter();
	}
}