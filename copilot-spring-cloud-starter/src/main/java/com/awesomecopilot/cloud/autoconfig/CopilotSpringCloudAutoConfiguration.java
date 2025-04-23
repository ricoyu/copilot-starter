package com.awesomecopilot.cloud.autoconfig;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.awesomecopilot.cloud.feign.aspect.IdempotentAspect;
import com.awesomecopilot.cloud.feign.interceptor.IdempotentInterceptor;
import com.awesomecopilot.cloud.feign.interceptor.TenantIdInterceptor;
import com.awesomecopilot.cloud.properties.IdemtotentProperties;
import com.awesomecopilot.cloud.properties.SentinelProperties;
import com.awesomecopilot.cloud.sentinel.RestBlockExceptionHandler;
import com.awesomecopilot.cloud.sentinel.auth.SentinelAuthRequestOriginParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * 网关异常处理自动配置
 * 加上@EnableConfigurationProperties({IdemtotentProperties.class, SentinelProperties.class})注解只是为了
 * SentinelProperties和IdemtotentProperties这两个属性类的@ConfigurationProperties(prefix = "copilot.sentinel")在Idea里面不显示error
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
@ConditionalOnWebApplication(type = SERVLET)
@EnableConfigurationProperties({IdemtotentProperties.class, SentinelProperties.class})
public class CopilotSpringCloudAutoConfiguration {

	@Autowired
	private SentinelProperties sentinelProperties;

	@Autowired
	private IdemtotentProperties idemtotentProperties;

	/**
	 * 实现接口幂等性的Feign拦截器, 在发送请求调用其他微服务时, 往请求头里塞Idempotent头; 
	 * 接口超时重试的时候, Feign会携带之前塞的Idempotent请求头, 在目标方法上加@Idempotent注解, 
	 * 通过AOP拦截, 然后从Request中拿Idempotent头, 如果拿到就往Redis的HyperLogLog里面塞, 塞成功了
	 * 就允许调用, 否则认为是重复提交, 不执行目标方法
	 * @return
	 */
	@Bean
	@ConditionalOnProperty(name = "copilot.idemtotent.enabled", havingValue = "true", matchIfMissing = false)
	public IdempotentInterceptor idempotentInterceptor() {
		return new IdempotentInterceptor();
	}
	
	/**
	 * 在需要做幂等性控制的接口方法上加@Idempotent注解, 这个切面提供做这个注解的支持
	 * @return
	 */
	@Bean
	@ConditionalOnProperty(name = "copilot.idemtotent.enabled", havingValue = "true", matchIfMissing = false)
	public IdempotentAspect idempotentAspect() {
		return new IdempotentAspect();
	}
	
	@Bean
	@ConditionalOnProperty(name = "copilot.sentinel.rest-exception-enabled", havingValue = "true", matchIfMissing = true)
	public RestBlockExceptionHandler restBlockExceptionHandler() {
		return new RestBlockExceptionHandler();
	}
	
	@Bean
	@ConditionalOnProperty(name = "copilot.sentinel.enabled", havingValue = "true", matchIfMissing = true)
	@ConditionalOnMissingBean(SentinelResourceAspect.class)
	public SentinelResourceAspect sentinelResourceAspect() {
		return new SentinelResourceAspect();
	}

	@Bean
	@ConditionalOnMissingBean(SentinelAuthRequestOriginParser.class)
	@ConditionalOnProperty(name = "copilot.sentinel.sentinel-auth-enabled", havingValue = "true", matchIfMissing = false)
	public SentinelAuthRequestOriginParser requestOriginParser() {
		return new SentinelAuthRequestOriginParser();
	}

	/**
	 * 用于在feign调用的时候传递请求头中的租户ID
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(TenantIdInterceptor.class)
	public TenantIdInterceptor tenantIdInterceptor() {
		return new TenantIdInterceptor();
	}

}
