package com.awesomecopilot.boot.web.autoconfig;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * 当关闭 Spring Boot 默认静态资源映射时，springdoc 不会暴露 /webjars/**，
 * Swagger UI 的 JS/CSS 将无法加载。这里补回 webjars 映射。
 */
@Configuration
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnClass(name = "org.springdoc.core.configuration.SpringDocConfiguration")
@ConditionalOnProperty(name = "spring.web.resources.add-mappings", havingValue = "false")
public class CopilotSwaggerResourceAutoConfiguration implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/webjars/**")
				.addResourceLocations("classpath:/META-INF/resources/webjars/");
	}
}
