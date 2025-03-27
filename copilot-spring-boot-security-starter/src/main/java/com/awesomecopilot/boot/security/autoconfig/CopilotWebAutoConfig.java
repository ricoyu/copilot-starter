package com.awesomecopilot.boot.security.autoconfig;

import com.awesomecopilot.boot.security.intercepter.TokenBasedAntiDupSubmitIntercepter;
import com.awesomecopilot.boot.security.props.CopilotSecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 这里目前只配置了一个防止重复提交的拦截器
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
@EnableConfigurationProperties({CopilotSecurityProperties.class})
public class CopilotWebAutoConfig implements WebMvcConfigurer {
	
	@Autowired
	private CopilotSecurityProperties properties;
	
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		if (properties.isAntiDuplicateSubmit()) {
			registry.addInterceptor(new TokenBasedAntiDupSubmitIntercepter()); //实现防止重复提交
		}
		WebMvcConfigurer.super.addInterceptors(registry);
	}
}
