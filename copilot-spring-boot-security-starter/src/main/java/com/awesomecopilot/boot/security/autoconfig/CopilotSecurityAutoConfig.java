package com.awesomecopilot.boot.security.autoconfig;

import com.awesomecopilot.boot.security.controller.RsaController;
import com.awesomecopilot.boot.security.controller.VerifyCodeController;
import com.awesomecopilot.boot.security.handler.RestAccessDeniedHandler;
import com.awesomecopilot.boot.security.processor.AuthUtilsInitializePostProcessor;
import com.awesomecopilot.boot.security.props.CopilotSecurityProperties;
import com.awesomecopilot.security.advice.RestSecurityExceptionAdvice;
import com.awesomecopilot.security.advice.TokenEndpointLoggerAspect;
import com.awesomecopilot.security.endpoint.RestAuthenticationEntryPoint;
import com.awesomecopilot.security.listener.AuthenticationFailureListener;
import com.awesomecopilot.security.processor.ObjectMapperBeanPostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.core.GrantedAuthorityDefaults;

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
@Configuration
@EnableConfigurationProperties({CopilotSecurityProperties.class})
public class CopilotSecurityAutoConfig {
	
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
	
	@Bean
	@ConditionalOnMissingBean(RestSecurityExceptionAdvice.class)
	public RestSecurityExceptionAdvice restSecurityExceptionAdvice() {
		return new RestSecurityExceptionAdvice();
	}
	
	/**
	 * 用来打印error log的
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(TokenEndpointLoggerAspect.class)
	public TokenEndpointLoggerAspect tokenEndpointLoggerAspect() {
		return new TokenEndpointLoggerAspect();
	}
	
	/**
	 * 用来设置SpringSecurity中@Secured("ROLE_custom-rule")等注解指定的角色名字是否需要加ROLE_前缀
	 */
	@Bean
	public GrantedAuthorityDefaults grantedAuthorityDefaults() {
		return new GrantedAuthorityDefaults(properties.getRolePrefix());
	}
	
	@Bean
	public RestAccessDeniedHandler restAccessDeniedHandler() {
		return new RestAccessDeniedHandler();
	}
	
	@Bean
	public RestAuthenticationEntryPoint restAuthenticationEntryPoint() {
		return new RestAuthenticationEntryPoint();
	}
	
	/**
	 * 根据是否启用验证码功能动态注册验证码Controller
	 */
	@Bean
	@ConditionalOnProperty(prefix = "copilot.security.pic-code", name = "enabled", havingValue = "true", matchIfMissing = false)
	public VerifyCodeController verifyCodeController() {
		return new VerifyCodeController();
	}
	
	@Bean
	@ConditionalOnProperty(prefix = "copilot.security", name = "auth-center-enabled", havingValue = "true", matchIfMissing = false)
	public RsaController rsaController() {
		properties.getWhiteList().add("/public-key");
		return new RsaController();
	}
	
	@Bean
	public AuthenticationFailureListener authenticationFailureListener() {
		return new AuthenticationFailureListener();
	}
	
}
