package com.awesomecopilot.gateway.autoconfig;

import com.awesomecopilot.cloud.gateway.advice.GatewayExceptionHandlerAdvice;
import com.awesomecopilot.cloud.gateway.handler.CopilotErrorWebExceptionHandler;
import com.awesomecopilot.cloud.gateway.properties.CopilotGatewayExceptionProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.WebProperties.Resources;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.Collections;
import java.util.List;

/**
 * 网关异常处理自动配置
 * <p>
 * Copyright: Copyright (c) 2020-05-02 10:45
 * <p>
 * Company: Sexy Uncle Inc.
 * <p>
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(WebFluxConfigurer.class)
@AutoConfigureBefore(WebFluxAutoConfiguration.class)
//@EnableConfigurationProperties({ServerProperties.class, Resources.class, CopilotGatewayExceptionProperties.class})
@EnableConfigurationProperties({ServerProperties.class,CopilotGatewayExceptionProperties.class})
public class CopilotExceptionAutoConfiguration {
	
	private ServerProperties serverProperties;
	
	private ApplicationContext applicationContext;
	
	private List<ViewResolver> viewResolvers;
	
	private ServerCodecConfigurer serverCodecConfigurer;
	
	public CopilotExceptionAutoConfiguration(ServerProperties serverProperties,
	                                         ObjectProvider<List<ViewResolver>> viewResolversProvider,
	                                         ServerCodecConfigurer serverCodecConfigurer,
	                                         ApplicationContext applicationContext) {
		this.serverProperties = serverProperties;
		this.applicationContext = applicationContext;
		this.viewResolvers = viewResolversProvider.getIfAvailable(() -> Collections.emptyList());
		this.serverCodecConfigurer = serverCodecConfigurer;
	}
    
    /**
     * ErrorWebExceptionHandler把实际错误处理交给GatewayExceptionHandlerAdvice
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(name = "gatewayExceptionHandlerAdvice")
    public GatewayExceptionHandlerAdvice gatewayExceptionHandlerAdvice() {
        return new GatewayExceptionHandlerAdvice();
    }
	
	@Bean
	public ErrorWebExceptionHandler errorWebExceptionHandler(ErrorAttributes errorAttributes) {
		DefaultErrorWebExceptionHandler exceptionHandler = new CopilotErrorWebExceptionHandler(
				errorAttributes, new Resources(),
				this.serverProperties.getError(), this.applicationContext);
		exceptionHandler.setViewResolvers(this.viewResolvers);
		exceptionHandler.setMessageWriters(this.serverCodecConfigurer.getWriters());
		exceptionHandler.setMessageReaders(this.serverCodecConfigurer.getReaders());
		return exceptionHandler;
	}
}
