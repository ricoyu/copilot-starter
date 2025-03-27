package com.awesomecopilot.boot.web.autoconfig;

import com.awesomecopilot.boot.web.intercepter.RateLimitIntercepter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * <p>
 * Copyright: (C), 2021-05-28 17:00
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Configuration
public class CopilotWebAutoConfig implements WebMvcConfigurer {
	
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new RateLimitIntercepter()); //实现应用限流
		WebMvcConfigurer.super.addInterceptors(registry);
	}
}
