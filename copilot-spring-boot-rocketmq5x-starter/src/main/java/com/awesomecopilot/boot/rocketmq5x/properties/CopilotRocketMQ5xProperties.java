package com.awesomecopilot.boot.rocketmq5x.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <p>
 * Copyright: (C), 2022-10-25 8:24
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "copilot.rocket5x")
public class CopilotRocketMQ5xProperties {
	
	/**
	 * RocketMQ接口幂等性开关
	 */
	private Idempotent idempotent;
	
	/**
	 * RocketMQ接口幂等性
	 */
	@Data
	public static class Idempotent {
		
		/**
		 * 是否启用RocketMQ接口幂等性, 在消费者的onMessage(MessageExit msg)方法中添加@Idempotent注解
		 * onMessage必须用MessageExit对象来接收消息, 否则@Idempotent注解不生效
		 */
		private boolean enabled = false;
	}
}