package com.awesomecopilot.cloud.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "copilot.lb.canary-release")
public class LBProperties {
	
	/**
	 * 是否启用金丝雀发布, 启用后可以做到灰度发布, 每个开发者只会调用到自己的微服务而不会调用到团队其他成员启动的微服务
	 */
	private boolean enabled = false;
}
