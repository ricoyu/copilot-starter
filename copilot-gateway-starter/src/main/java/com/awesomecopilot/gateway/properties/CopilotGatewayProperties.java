package com.awesomecopilot.gateway.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <p>
 * Copyright: (C), 2020/4/24 11:24
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@ConfigurationProperties(prefix = "copilot.gateway")
public class CopilotGatewayProperties {
	
	private boolean timeMonitorFilterEnabled = true;
	
	private boolean timeBetweenRouteEnabled = true;

	private CORS cors = new CORS();

	public boolean isTimeMonitorFilterEnabled() {
		return timeMonitorFilterEnabled;
	}

	public void setTimeMonitorFilterEnabled(boolean timeMonitorFilterEnabled) {
		this.timeMonitorFilterEnabled = timeMonitorFilterEnabled;
	}

	public boolean isTimeBetweenRouteEnabled() {
		return timeBetweenRouteEnabled;
	}

	public void setTimeBetweenRouteEnabled(boolean timeBetweenRouteEnabled) {
		this.timeBetweenRouteEnabled = timeBetweenRouteEnabled;
	}

	public CORS getCors() {
		return cors;
	}

	public void setCors(CORS cors) {
		this.cors = cors;
	}

	public static class CORS {

		/**
		 * 是否自动配置允许跨域
		 */
		private boolean enabled;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}
}
