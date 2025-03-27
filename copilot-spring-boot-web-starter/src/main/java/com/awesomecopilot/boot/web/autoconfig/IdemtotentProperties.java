package com.awesomecopilot.boot.web.autoconfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用来开启接口幂等性支持
 * <p>
 * Copyright: (C), 2023-03-18 17:28
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "copilot.idemtotent")
public class IdemtotentProperties {
	
	/**
	 * 是否启用接口幂等性控制, 默认true
	 */
	private boolean enabled = true;
}
