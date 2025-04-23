package com.awesomecopilot.boot.web.autoconfig;

import com.awesomecopilot.boot.web.autoconfig.properties.CopilotJacksonProperties;
import com.awesomecopilot.boot.web.autoconfig.properties.CopilotOrmProperties;
import com.awesomecopilot.boot.web.autoconfig.properties.CopilotProperties;
import com.awesomecopilot.common.spring.annotation.processor.PostInitializeGroupOrderedBeanProcessor;
import com.awesomecopilot.common.spring.context.ApplicationContextHolder;
import com.awesomecopilot.common.spring.transaction.TransactionEvents;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * <p>
 * Copyright: (C), 2020/4/14 16:22
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@EnableConfigurationProperties({CopilotProperties.class, CopilotJacksonProperties.class, CopilotOrmProperties.class})
@Configuration
public class CopilotAutoConfiguration {

	private static Logger log = LoggerFactory.getLogger(CopilotAutoConfiguration.class);

	@Autowired
	private CopilotProperties copilotProperties;

	@PostConstruct
	public void started() {
		TimeZone.setDefault(TimeZone.getTimeZone(copilotProperties.getTimezone()));
	}

	@Bean
	@ConditionalOnMissingBean(ApplicationContextHolder.class)
	public ApplicationContextHolder applicationContextHolder() {
		return new ApplicationContextHolder();
	}

	@Bean
	@ConditionalOnMissingBean(TransactionEvents.class)
	@ConditionalOnProperty(value = "copilot.asyncTransaction", matchIfMissing = true, havingValue = "true")
	public TransactionEvents transactionEvents() {
		return new TransactionEvents();
	}

	@Bean
	@ConditionalOnMissingBean(PostInitializeGroupOrderedBeanProcessor.class)
	@ConditionalOnProperty(value = "copilot.enablePostInitialize", matchIfMissing = true, havingValue = "true")
	public PostInitializeGroupOrderedBeanProcessor postInitializeBeanProcessor() {
		PostInitializeGroupOrderedBeanProcessor beanProcessor = new PostInitializeGroupOrderedBeanProcessor();
		beanProcessor.setContextCount(1);
		return beanProcessor;
	}

}
