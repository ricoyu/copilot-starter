package com.awesomecopilot.gateway.properties;

import lombok.Data;
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
@Data
@ConfigurationProperties(prefix = "copilot.gateway")
public class CopilotGatewayProperties {
	
	private boolean timeMonitorFilterEnabled = true;
	
	private boolean timeBetweenRouteEnabled = true;
}
