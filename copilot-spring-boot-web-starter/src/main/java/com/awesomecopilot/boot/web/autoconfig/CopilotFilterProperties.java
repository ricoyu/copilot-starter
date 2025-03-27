package com.awesomecopilot.boot.web.autoconfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
@Data
@ConfigurationProperties(prefix = "copilot.filter")
public class CopilotFilterProperties {
	
	/**
	 * 是否注册HttpServletRequestRepeatedReadFilter, 以开启重复读取RequestBody功能
	 */
	private boolean repeatedRead = false;
}
