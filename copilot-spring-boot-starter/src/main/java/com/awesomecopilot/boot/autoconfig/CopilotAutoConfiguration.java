package com.awesomecopilot.boot.autoconfig;

import com.awesomecopilot.boot.autoconfig.condition.ConditionalOnCopilotProperty;
import com.awesomecopilot.boot.autoconfig.processor.ObjectMapperBeanPostProcessor;
import com.awesomecopilot.boot.autoconfig.properties.CopilotJacksonProperties;
import com.awesomecopilot.boot.autoconfig.properties.CopilotOrmProperties;
import com.awesomecopilot.boot.autoconfig.properties.CopilotProperties;
import com.awesomecopilot.common.spring.annotation.processor.PostInitializeGroupOrderedBeanProcessor;
import com.awesomecopilot.common.spring.context.ApplicationContextHolder;
import com.awesomecopilot.common.spring.transaction.TransactionEvents;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	@ConditionalOnCopilotProperty("copilot.async-transaction")
	public TransactionEvents transactionEvents() {
		return new TransactionEvents();
	}

	@Bean
	@ConditionalOnMissingBean(PostInitializeGroupOrderedBeanProcessor.class)
	@ConditionalOnCopilotProperty("copilot.enable-post-initialize")
	public PostInitializeGroupOrderedBeanProcessor postInitializeBeanProcessor() {
		PostInitializeGroupOrderedBeanProcessor beanProcessor = new PostInitializeGroupOrderedBeanProcessor();
		beanProcessor.setContextCount(1);
		return beanProcessor;
	}

	/**
	 * 把容器的 ObjectMapper 交给静态 JacksonUtils, 并应用 copilot.jackson.* 配置
	 * (fieldNameQuote、自定义序列化器/反序列化器).
	 * <p>
	 * 与 web-starter 的 CopilotJacksonObjectMapperPostProcessor 分工: 那边在 ObjectMapper bean
	 * 初始化阶段做装饰(让 MVC converter 尽早拿到增强 mapper), 本处理器在全部单例就绪后补做
	 * 把 mapper 交给 JacksonUtils 与 copilot.jackson.* 配置; 两边共用 ObjectMapperDecorationTracker,
	 * 装饰不会重复. 此前本类没有任何注册点, copilot.jackson.* 在非 Web 应用整块不生效(评审报告 P1-1).
	 */
	@Bean
	@ConditionalOnMissingBean(ObjectMapperBeanPostProcessor.class)
	public ObjectMapperBeanPostProcessor objectMapperBeanPostProcessor(
			ObjectProvider<ObjectMapper> objectMapperProvider,
			CopilotJacksonProperties copilotJacksonProperties) {
		return new ObjectMapperBeanPostProcessor(objectMapperProvider, copilotJacksonProperties);
	}
}
