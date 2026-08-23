package com.awesomecopilot.boot.web.autoconfig;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * Jackson 增强改由 {@link CopilotJacksonObjectMapperPostProcessor} 在 ObjectMapper 初始化阶段完成，
 * 不再替换 Spring MVC 默认 HttpMessageConverter，避免影响 Swagger UI 等静态资源与 OpenAPI 输出。
 */
@Configuration
@ConditionalOnWebApplication(type = SERVLET)
public class HttpMessageConverterAutoConfiguration implements WebMvcConfigurer {

	@Bean
	public static CopilotJacksonObjectMapperPostProcessor copilotJacksonObjectMapperPostProcessor(
			Environment environment) {
		return new CopilotJacksonObjectMapperPostProcessor(environment);
	}

	@Override
	public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
		/*
		 * 仅增强 text/plain 的 UTF-8 输出，避免中文乱码。
		 * 不要在此处注册支持 application/json 的 StringHttpMessageConverter，
		 * 否则会抢占 Jackson 处理 JSON 响应，导致 Swagger/OpenAPI 异常。
		 */
		StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter(UTF_8);
		stringHttpMessageConverter.setSupportedMediaTypes(List.of(MediaType.TEXT_PLAIN));
		converters.add(0, stringHttpMessageConverter);
	}
}
