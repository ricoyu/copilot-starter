package com.awesomecopilot.boot.rocketmq5x.constants;

public final class RocketMQ5xConstants {
	
	/**
	 * 用来做幂等性控制的消息头名称, 发送放在发送的时候将一个业务上唯一的值塞到UNIQ_KEY消息头 中
	 */
	public static final String UNIQ_KEY = "UNIQ_KEY";
	
	/**
	 * 幂等性控制是基于Redis的SET来做的, 这个常量指定的是key的名称
	 *
	 * @deprecated 全局单 SET 永不过期已废弃, 现实现为每令牌独立 key "rocketmq:idempotent:{uniqueValue}"
	 * + 原子 setnx+TTL, 见 RocketMQ5xIdempotentAspect#redisKey
	 */
	@Deprecated
	public static final String KEY = "rocketmq:idempotent";
}