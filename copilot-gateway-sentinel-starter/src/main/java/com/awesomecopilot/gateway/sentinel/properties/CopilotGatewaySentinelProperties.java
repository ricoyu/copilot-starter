package com.awesomecopilot.gateway.sentinel.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "copilot.gateway")
public class CopilotGatewaySentinelProperties {

	/**
	 * 是否开启Sentinel网关限流, 默认false
	 */
	private Sentinel sentinel = new Sentinel();

	public Sentinel getSentinel() {
		return sentinel;
	}

	public void setSentinel(Sentinel sentinel) {
		this.sentinel = sentinel;
	}

	public static class Sentinel {

		private boolean enabled;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}
}
