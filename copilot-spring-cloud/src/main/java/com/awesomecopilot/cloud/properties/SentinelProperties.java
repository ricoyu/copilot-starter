package com.awesomecopilot.cloud.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <p>
 * Copyright: (C), 2023-03-20 16:28
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@ConfigurationProperties(prefix = "copilot.sentinel")
public class SentinelProperties {
	
	/**
	 * 开启后会自动注册Bean: SentinelResourceAspect
	 * 加了@SentinelResource注解的资源, 是通过SentinelResourceAspect来处理的
	 */
	private boolean enabled;

	/**
	 * 控制是否注册 RestBlockExceptionHandler bean
	 */
	private boolean restExceptionEnabled = true;

	/**
	 * 控制根据调用方限流以及授权规则
	 * 这两者实际上是一套东西
	 */
	private AuthRule authRule = new AuthRule();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isRestExceptionEnabled() {
		return restExceptionEnabled;
	}

	public void setRestExceptionEnabled(boolean restExceptionEnabled) {
		this.restExceptionEnabled = restExceptionEnabled;
	}

	public AuthRule getAuthRule() {
		return authRule;
	}

	public void setAuthRule(AuthRule authRule) {
		this.authRule = authRule;
	}

	/**
	 * 同时控制流控中的根据调用方限流和授权规则
	 */
	public static class AuthRule {

		/**
		 * 是否启用 根据调用方限流 和 授权规则
		 * 开启不影响现有功能, 所以默认开启
		 */
		private boolean enabled = true;
		/**
		 * 支持授权规则的Feign拦截器传递的请求头名字, 以及 RequestOriginParser 获取的请求头名字, 默认Origin
		 */
		private String header = "Auth-Origin";

		public String getHeader() {
			return header;
		}

		public void setHeader(String header) {
			this.header = header;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}
}
