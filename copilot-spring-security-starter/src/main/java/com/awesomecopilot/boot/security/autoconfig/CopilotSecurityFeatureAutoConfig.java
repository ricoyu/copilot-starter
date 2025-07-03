package com.awesomecopilot.boot.security.autoconfig;

import com.awesomecopilot.security.advice.RestSecurityExceptionAdvice;
import com.awesomecopilot.security.controller.VerifyCodeController;
import com.awesomecopilot.security.handler.RestAccessDeniedHandler;
import com.awesomecopilot.security.intercepter.TokenBasedAntiDupSubmitIntercepter;
import com.awesomecopilot.security.listener.AuthenticationFailureListener;
import com.awesomecopilot.security.processor.AuthUtilsInitializePostProcessor;
import com.awesomecopilot.security.processor.ObjectMapperBeanPostProcessor;
import com.awesomecopilot.security.properties.CopilotSecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 这里配置的是一些额外的丰富功能
 * <p>
 * Copyright: (C), 2020-08-14 13:55
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
//@Configuration
//@EnableConfigurationProperties({CopilotSecurityProperties.class})
public class CopilotSecurityFeatureAutoConfig implements WebMvcConfigurer {
	
	@Autowired
	private CopilotSecurityProperties properties;
	
	@Bean
	public AuthUtilsInitializePostProcessor authUtilsInitializePostProcessor() {
		return new AuthUtilsInitializePostProcessor();
	}
	
	@Bean
	public ObjectMapperBeanPostProcessor securityObjectMapperPostProcessor() {
		return new ObjectMapperBeanPostProcessor();
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		if (properties.getFeature().isAntiDuplicateSubmit()) {
			registry.addInterceptor(new TokenBasedAntiDupSubmitIntercepter()); //实现防止重复提交
		}
		WebMvcConfigurer.super.addInterceptors(registry);
	}

	@Bean
	@ConditionalOnMissingBean(RestSecurityExceptionAdvice.class)
	public RestSecurityExceptionAdvice restSecurityExceptionAdvice() {
		return new RestSecurityExceptionAdvice();
	}
	
	/**
	 * 用来设置SpringSecurity中@Secured("ROLE_custom-rule")等注解指定的角色名字是否需要加ROLE_前缀
	 */
	@Bean
	public GrantedAuthorityDefaults grantedAuthorityDefaults() {
		return new GrantedAuthorityDefaults(properties.getUserPassLogin().getRolePrefix());
	}
	
	@Bean
	public RestAccessDeniedHandler restAccessDeniedHandler() {
		return new RestAccessDeniedHandler();
	}

	/**
	 * 根据是否启用验证码功能动态注册验证码Controller
	 */
	@Bean
	@ConditionalOnProperty(prefix = "copilot.security.feature.pic-code", name = "enabled", havingValue = "true", matchIfMissing = false)
	public VerifyCodeController verifyCodeController() {
		return new VerifyCodeController();
	}

	@Bean
	public AuthenticationFailureListener authenticationFailureListener() {
		return new AuthenticationFailureListener();
	}
	
}
