package com.awesomecopilot.boot.web.autoconfig;

import com.awesomecopilot.boot.web.websocket.WebSocketFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * Copyright: (C), 2020-09-11 17:14
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "copilot.websocket", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({CopilotWebsocketProperties.class})
public class CopilotWebsocketAutoConfiguration {
	
	@Autowired
	private CopilotWebsocketProperties copilotWebsocketProperties;
	
	@Bean
	public WebSocketFilter webSocketFilter() {
		return new WebSocketFilter();
	}

}
