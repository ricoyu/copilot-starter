package com.awesomecopilot.boot.web.autoconfig;

import com.awesomecopilot.boot.web.autoconfig.CopilotMvcProperties.CORS;
import com.awesomecopilot.boot.web.filter.ApiSignFilter;
import com.awesomecopilot.boot.web.filter.TenantIdFilter;
import com.awesomecopilot.boot.web.filter.TomcatThreadPoolStatisticFilter;
import com.awesomecopilot.boot.web.intercepter.IdempotentIntercepter;
import com.awesomecopilot.web.advice.GlobalBindingAdvice;
import com.awesomecopilot.web.advice.RestExceptionAdvice;
import com.awesomecopilot.web.context.support.CustomConversionServiceFactoryBean;
import com.awesomecopilot.web.converter.GenericEnumConverter;
import com.awesomecopilot.web.filter.HttpServletRequestRepeatedReadFilter;
import com.awesomecopilot.web.resolver.DateArgumentResolver;
import com.awesomecopilot.web.resolver.LocalDateArgumentResolver;
import com.awesomecopilot.web.resolver.LocalDateTimeArgumentResolver;
import com.awesomecopilot.web.resolver.LocalTimeArgumentResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.filter.OrderedCharacterEncodingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.REACTIVE;
import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

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
@Configuration
@ConditionalOnWebApplication(type = SERVLET)
@EnableConfigurationProperties({CopilotFilterProperties.class, CopilotMvcProperties.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CopilotMvcConfiguration implements WebMvcConfigurer {

	private Logger log = LoggerFactory.getLogger(CopilotMvcConfiguration.class);

	@Value("${copilot.filter.tenant.mandatory:false}")
	private boolean tenantIdMandatory=false;

	@Lazy
	@Autowired
	private CopilotMvcProperties copilotMvcProperties;

	/**
	 * 支持Controller方法参数里面日期类型的绑定
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(GlobalBindingAdvice.class)
	public GlobalBindingAdvice globalBindingAdvice() {
		return new GlobalBindingAdvice();
	}
	
	/**
	 * 忘记这个CustomConversionServiceFactoryBean是干啥用的了, controller方法enum参数绑定是下面#addFormatters方法里面配置的converter是来实现的
	 * @return
	 */
	@Bean
	public CustomConversionServiceFactoryBean conversionService() {
		CustomConversionServiceFactoryBean conversionServiceFactoryBean = new CustomConversionServiceFactoryBean();
		conversionServiceFactoryBean.getProperties().add("code");
		return conversionServiceFactoryBean;
	}
	
	/**
	 * 全局异常处理
	 *
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(RestExceptionAdvice.class)
	@ConditionalOnProperty(prefix = "copilot.mvc", value = "rest-exception-advice-enabled", havingValue = "true", matchIfMissing = true)
	public RestExceptionAdvice restExceptionAdvice() {
		return new RestExceptionAdvice();
	}

	@Bean
	@ConditionalOnProperty(prefix = "copilot.filter", value = "pool-statistic", havingValue = "true", matchIfMissing = false)
	public TomcatThreadPoolStatisticFilter poolStatisticFilter() {
		return new TomcatThreadPoolStatisticFilter();

	}

	@Bean
	@Primary
	public CharacterEncodingFilter characterEncodingFilter() {
		OrderedCharacterEncodingFilter filter = new OrderedCharacterEncodingFilter();
		filter.setEncoding("UTF-8");
		filter.setForceRequestEncoding(true);
		filter.setForceResponseEncoding(true);
		filter.setForceEncoding(true);
		filter.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return filter;
	}

	@Bean
	public FilterRegistrationBean<TenantIdFilter> tenantIdFilterRegistration() {
		FilterRegistrationBean<TenantIdFilter> registration = new FilterRegistrationBean<>();
		registration.setFilter(new TenantIdFilter(tenantIdMandatory));
		registration.addUrlPatterns("/*"); // 拦截所有请求
		registration.setOrder(1); // 设置优先级（数值越小优先级越高）
		return registration;
	}
	
	@Bean
	@ConditionalOnProperty(prefix = "copilot.filter", value = "repeated-read")
	public FilterRegistrationBean requestRepeatedReadFilter() {
		FilterRegistrationBean registrationBean = new FilterRegistrationBean();
		registrationBean.setFilter(new HttpServletRequestRepeatedReadFilter());
		return registrationBean;
	}

	@Bean
	public ApiSignFilter apiSignFilter() {
		return new ApiSignFilter();
	}
	
/*	@Bean
	public IdempotentTokenController idempotentTokenController() {
		return new IdempotentTokenController();
	}*/
	
	/**
	 * 支持Controller方法Enum类型参数绑定，可以按名字，也可以按制定的属性
	 */
	@Override
	public void addFormatters(FormatterRegistry registry) {
		Set<String> properties = new HashSet<>();
		properties.add("code");
		properties.add("desc");
		registry.addConverter(new GenericEnumConverter(properties));
		WebMvcConfigurer.super.addFormatters(registry);
	}
	
	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new DateArgumentResolver());
		resolvers.add(new LocalDateArgumentResolver());
		resolvers.add(new LocalDateTimeArgumentResolver());
		resolvers.add(new LocalTimeArgumentResolver());
	}
	
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		CORS cors = copilotMvcProperties.getCors();
		if (!cors.isEnabled()) {
			return;
		}
		registry.addMapping("/**")
				.allowedOriginPatterns(cors.getAllowedOrigins().toArray(new String[0]))
				.allowedHeaders(cors.getAllowedHeaders().toArray(new String[0]))
				.allowedMethods(cors.getAllowedMethods().toArray(new String[0]))
				.allowCredentials(cors.isAllowCredentials())
				.maxAge(cors.getMaxAge());
	}
	
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new IdempotentIntercepter());
		//基于token的幂等性保证
		WebMvcConfigurer.super.addInterceptors(registry);
	}
}
