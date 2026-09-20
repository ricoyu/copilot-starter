package com.awesomecopilot.boot.rocketmq5x.properties;

import com.awesomecopilot.boot.rocketmq5x.aspect.RocketMQ5xIdempotentAspect;
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
	private Idempotent idempotent = new Idempotent();
	
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
		
		/**
		 * 幂等令牌在 Redis 的存活秒数(幂等窗口). 默认 21600 秒=6小时: broker 默认
		 * messageDelayLevel 表下 16 次重试全部跑完约 4h46m, 留余量.
		 * 重试间隔在 broker 侧可配置(messageDelayLevel), 非常规配置请显式调大.
		 * 默认值单源: 引用 RocketMQ5xIdempotentAspect.DEFAULT_TTL_SECONDS.
		 */
		private long ttlSeconds = RocketMQ5xIdempotentAspect.DEFAULT_TTL_SECONDS;
		
		/**
		 * Redis 不可用时的策略. 默认 false=fail-closed: 抛回使消息稍后重试, 不做无幂等保护的消费;
		 * 配 true=fail-open: 故障窗口内放行消费(幂等保护暂停), 可用性优先
		 */
		private boolean failOpenOnRedisError = false;
	}
}