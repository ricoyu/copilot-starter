package com.awesomecopilot.boot.web.autoconfig;

import com.awesomecopilot.boot.autoconfig.processor.ObjectMapperDecorationTracker;
import com.awesomecopilot.json.ObjectMapperDecorator;
import com.awesomecopilot.json.jackson.serializer.GlobalHtmlEscapeSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * 在 ObjectMapper Bean 初始化阶段完成 Copilot 增强，
 * 让 Spring MVC 默认的 MappingJackson2HttpMessageConverter 直接使用增强后的 ObjectMapper，
 * 无需替换 HttpMessageConverter 列表。
 */
public class CopilotJacksonObjectMapperPostProcessor implements BeanPostProcessor, Ordered {

	private final Environment environment;

	public CopilotJacksonObjectMapperPostProcessor(Environment environment) {
		this.environment = environment;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (!(bean instanceof ObjectMapper objectMapper)) {
			return bean;
		}
		if (!ObjectMapperDecorationTracker.markDecorated(objectMapper)) {
			return bean;
		}

		ObjectMapperDecorator decorator = new ObjectMapperDecorator();
		decorator.decorate(objectMapper);

		Boolean xssEnabled = environment.getProperty("copilot.filter.xss-enabled", Boolean.class);
		if (Boolean.TRUE.equals(xssEnabled)) {
			SimpleModule xssModule = new SimpleModule("xssModule");
			xssModule.addSerializer(new GlobalHtmlEscapeSerializer());
			objectMapper.registerModule(xssModule);
		}
		return bean;
	}

	@Override
	public int getOrder() {
		// 在 Spring Security 的 ObjectMapper MixIn 之后执行
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}
}
