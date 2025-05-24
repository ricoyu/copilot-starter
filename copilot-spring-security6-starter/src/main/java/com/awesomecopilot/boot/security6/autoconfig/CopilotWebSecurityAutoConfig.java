package com.awesomecopilot.boot.security6.autoconfig;

import com.awesomecopilot.security6.endpoint.RestAuthenticationEntryPoint;
import com.awesomecopilot.security6.expression.handler.WildcardMethodSecurityExpressionHandler;
import com.awesomecopilot.security6.filter.PreAuthenticationFilter;
import com.awesomecopilot.security6.filter.RestoreAuthenticationFilter;
import com.awesomecopilot.security6.filter.SecurityExceptionFilter;
import com.awesomecopilot.security6.filter.UsernamePasswordAuthenticationFilter;
import com.awesomecopilot.security6.filter.VerifyCodeFilter;
import com.awesomecopilot.security6.filter.XSSFilter;
import com.awesomecopilot.security6.handler.LoginFailureHandler;
import com.awesomecopilot.security6.handler.LoginSuccessHandler;
import com.awesomecopilot.security6.handler.LogoutSuccessHandler;
import com.awesomecopilot.security6.properties.CopilotSecurityProperties;
import com.awesomecopilot.security6.service.PreAuthenticationUserDetailsService;
import com.awesomecopilot.web.filter.HttpServletRequestRepeatedReadFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter;

import java.util.List;

import static com.awesomecopilot.security6.constants.SecurityConstants.PIC_CODE_URL;

/**
 * 这里配置SpringSecurity的主要核心组件/主流程
 *
 * @EnableGlobalMethodSecurity(jsr250Enabled = true) 的作用是启用 JSR-250 标准注解（如 @RolesAllowed, @PermitAll, @DenyAll）来保护
 * Spring 方法
 * <p>
 * @RolesAllowed({"ADMIN", "SUPER_USER"}) <br/>
 * @PermitAll <br/>
 * @DenyAll <br/>
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@EnableConfigurationProperties({CopilotSecurityProperties.class})
public class CopilotWebSecurityAutoConfig {

	@Autowired
	private CopilotSecurityProperties properties;

	@Autowired
	private RestAuthenticationEntryPoint restAuthenticationEntryPoint;

	@Autowired(required = false)
	private UserDetailsService userDetailsService;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		/*
		 * 默认情况下，Spring Security 会启用 CSRF 保护，而表单登录需要 CSRF token。
		 * 如果不关闭, 到了UsernamePasswordAuthenticationFilter那边, 当前的url已经被重定向为/error了, 导致无法登录
		 */
		http.csrf(AbstractHttpConfigurer::disable);
		//Spring Security不会创建或使用HTTP会话（HttpSession）来存储任何与用户身份验证相关的信息。
		http.sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(restAuthenticationEntryPoint));
		http.authorizeHttpRequests(authorize -> {
					//可以通过配置copilot.security.white-list来配置白名单, 即这些白名单中得url不需要认证就可以访问
					authorize.requestMatchers(anonymousUrls()).permitAll();
					authorize.anyRequest().authenticated();
				});

		http.formLogin(form -> {
			form.loginProcessingUrl(properties.getUserPassLogin().getLoginUrl());
		});

		http.logout(logout -> logout
				.logoutUrl(properties.getUserPassLogin().getLogoutUrl())
				.logoutSuccessHandler(logoutSuccessHandler()));

		/*
		 * 在过滤器链上发生未处理的异常时, RestExceptionAdvice是处理不到的,
		 * 所以通过这个Filter来统一捕获, 然后通过HandlerExceptionResolver代理给RestExceptionAdvice来处理
		 */
		http.addFilterBefore(securityExceptionFilter(), UsernamePasswordAuthenticationFilter.class);
		//提供SpringSecurity过滤器链对Request Body的可重复读取
		http.addFilterBefore(new HttpServletRequestRepeatedReadFilter(), WebAsyncManagerIntegrationFilter.class);
		http.addFilterBefore(preAuthenticationFilter(authenticationManager(http)),
						UsernamePasswordAuthenticationFilter.class);
		http.addFilterBefore(new RestoreAuthenticationFilter(), LogoutFilter.class);
		http.addFilterAt(usernamePasswordAuthenticationFilter(authenticationManager(http)),
				org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

		CopilotSecurityProperties.Feature.PicCode picCode = properties.getFeature().getPicCode();
		if (picCode != null && picCode.isEnabled()) {
			http.addFilterBefore(verifyCodeFilter(), UsernamePasswordAuthenticationFilter.class);
		}

		//http.headers(headers -> {
		//	headers.addHeaderWriter(new XXssProtectionHeaderWriter())
		//			.addHeaderWriter(new ContentSecurityPolicyHeaderWriter("script-src 'self'"));
		//});
		return http.build();
	}

	//@Bean
	//public WebSecurityCustomizer webSecurityCustomizer() {
	//	return (web) -> web.ignoring().requestMatchers("/resources/**", "/static/**");
	//}

	@Bean
	public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
		AuthenticationManagerBuilder authenticationManagerBuilder =
				http.getSharedObject(AuthenticationManagerBuilder.class);
		authenticationManagerBuilder.authenticationProvider(preAuthenticationProvider());
		if (properties.getUserPassLogin().isEnabled()) {
			authenticationManagerBuilder.authenticationProvider(daoAuthenticationProvider());
		}
		return authenticationManagerBuilder.build();
	}

	@Bean
	public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
		return new WildcardMethodSecurityExpressionHandler();
	}

	@Bean
	@ConditionalOnProperty(prefix = "copilot.security6", name = "user-pass-login.enabled", havingValue = "true", matchIfMissing = false)
	public AuthenticationProvider daoAuthenticationProvider() {
		DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
		authenticationProvider.setUserDetailsService(userDetailsService);
		authenticationProvider.setPasswordEncoder(passwordEncoder());
		return authenticationProvider;
	}

	@Bean
	public AuthenticationProvider preAuthenticationProvider() {
		PreAuthenticatedAuthenticationProvider authenticationProvider = new PreAuthenticatedAuthenticationProvider();
		authenticationProvider.setPreAuthenticatedUserDetailsService(preAuthenticationUserDetailsService());
		return authenticationProvider;
	}

	@Bean
	@ConditionalOnMissingBean(PasswordEncoder.class)
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	@ConditionalOnMissingBean(PreAuthenticationUserDetailsService.class)
	public PreAuthenticationUserDetailsService preAuthenticationUserDetailsService() {
		return new PreAuthenticationUserDetailsService();
	}

	@Bean
	public SecurityExceptionFilter securityExceptionFilter() {
		return new SecurityExceptionFilter();
	}

	@Bean
	public PreAuthenticationFilter preAuthenticationFilter(AuthenticationManager authenticationManager) {
		PreAuthenticationFilter authenticationFilter = new PreAuthenticationFilter();
		authenticationFilter.setAuthenticationManager(authenticationManager);
		return authenticationFilter;
	}

	@Bean
	@ConditionalOnProperty(prefix = "copilot.security6.feature.pic-code", name = "enabled", havingValue = "true", matchIfMissing
			= false)
	public VerifyCodeFilter verifyCodeFilter() {
		properties.getWhiteList().add("/pic-code");
		return new VerifyCodeFilter();
	}

	/*
	 * 不加@Bean注解的原因是不想把UsernamePasswordAuthenticationFilter加入到SpringMVC的过滤器链里面, 否则SpringSecurity的filterChain里面会执行一次
	 * UsernamePasswordAuthenticationFilter, SpringMVC的过滤器链中还会执行一次UsernamePasswordAuthenticationFilter, 导致这个过滤器会被执行两次
	 */
	public UsernamePasswordAuthenticationFilter usernamePasswordAuthenticationFilter(AuthenticationManager authenticationManager) {
		//properties.getWhiteList().add(properties.getUserPassLogin().getLoginUrl());
		UsernamePasswordAuthenticationFilter authenticationFilter = new UsernamePasswordAuthenticationFilter();
		authenticationFilter.setAuthenticationSuccessHandler(loginSuccessHandler());
		authenticationFilter.setAuthenticationFailureHandler(loginFailureHandler());
		//authenticationFilter.setRequiresAuthenticationRequestMatcher(new AntPathRequestMatcher(properties.getUserPassLogin().getLoginUrl(), "POST"));
		authenticationFilter.setAuthenticationManager(authenticationManager);
		return authenticationFilter;
	}

	@Bean
	public FilterRegistrationBean<XSSFilter> xssFilter() {
		FilterRegistrationBean<XSSFilter> filter = new FilterRegistrationBean<>();
		filter.setFilter(new XSSFilter());
		filter.setOrder(Integer.MIN_VALUE);
		return filter;
	}

	@Bean
	public AuthenticationSuccessHandler loginSuccessHandler() {
		return new LoginSuccessHandler();
	}

	@Bean
	public AuthenticationFailureHandler loginFailureHandler() {
		return new LoginFailureHandler();
	}

	@Bean
	public LogoutSuccessHandler logoutSuccessHandler() {
		return new LogoutSuccessHandler();
	}

	private String[] anonymousUrls() {
		List<String> whiteList = properties.getWhiteList();
		//登录uri不能加入认证白名单
		//whiteList.add(properties.getUserPassLogin().getLoginUrl());

		if (properties.getFeature().getPicCode() != null && properties.getFeature().getPicCode().isEnabled()) {
			whiteList.add(PIC_CODE_URL);
		}
		return whiteList.toArray(new String[0]);
	}

	@Bean
	public RestAuthenticationEntryPoint restAuthenticationEntryPoint() {
		return new RestAuthenticationEntryPoint();
	}
}
