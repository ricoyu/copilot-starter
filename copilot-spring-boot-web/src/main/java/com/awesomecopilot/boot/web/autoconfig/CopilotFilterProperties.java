package com.awesomecopilot.boot.web.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Copyright: (C), 2020-09-08 14:48
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@ConfigurationProperties(prefix = "copilot.filter")
public class CopilotFilterProperties {
	
	/**
	 * 是否注册HttpServletRequestRepeatedReadFilter, 以开启重复读取RequestBody功能
	 */
	private boolean repeatedRead = false;

	private Tenant tenant;

	public boolean isRepeatedRead() {
		return repeatedRead;
	}

	public void setRepeatedRead(boolean repeatedRead) {
		this.repeatedRead = repeatedRead;
	}

	public Tenant getTenant() {
		return tenant;
	}

	public void setTenant(Tenant tenant) {
		this.tenant = tenant;
	}

	public static class Tenant {
		
		private boolean mandatory;

		/**
		 * 忽略的url, 访问这些URL不需要携带Tenant-Id请求头
		 */
		private List<String> excludeUrls = new ArrayList<>();

		public boolean isMandatory() {
			return this.mandatory;
		}

		public void setMandatory(boolean mandatory) {
			this.mandatory = mandatory;
		}

		public List<String> getExcludeUrls() {
			return excludeUrls;
		}

		public void setExcludeUrls(List<String> excludeUrls) {
			this.excludeUrls = excludeUrls;
		}
	}
}
