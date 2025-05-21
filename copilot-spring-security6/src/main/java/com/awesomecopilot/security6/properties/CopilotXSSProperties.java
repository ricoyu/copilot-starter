package com.awesomecopilot.security6.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置是否开启XSS攻击防护
 * <p>
 * Copyright: (C), 2021-02-23 10:11
 * <p>
 * <p>
 * Company: Information & Data Security Solutions Co., Ltd.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "copilot.xss")
public class CopilotXSSProperties {
	
	/**
	 * 是否要开启抗XSS攻击, 默认false
	 */
	private boolean enabled = false;
	
}
