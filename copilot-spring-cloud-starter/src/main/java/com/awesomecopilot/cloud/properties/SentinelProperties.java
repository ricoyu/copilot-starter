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
	 * 开启后会自动注册Bean: RestBlockExceptionHandler, SentinelResourceAspect
	 */
	private boolean enabled;

	private boolean restExceptionEnabled = true;

	/**
	 * 是否开启sentinel的授权规则限流, 开启后会注册一个MyRequestOriginParser Bean
	 * 只需要在feign的被调用方开启
	 */
	private Boolean sentinelAuthEnabled = true;

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

	public Boolean getSentinelAuthEnabled() {
		return sentinelAuthEnabled;
	}

	public void setSentinelAuthEnabled(Boolean sentinelAuthEnabled) {
		this.sentinelAuthEnabled = sentinelAuthEnabled;
	}
}
