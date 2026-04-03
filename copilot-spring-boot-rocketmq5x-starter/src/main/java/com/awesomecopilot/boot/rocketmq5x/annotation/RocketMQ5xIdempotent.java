package com.awesomecopilot.boot.rocketmq5x.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 对RocketMQ消息消费做幂等性处理, 已经成功消费的消息不会被再次消费
 * <p>
 * RocketMQ在整合Spring的时候, 假设pullBatchSize=100,consumeMessageBatchMaxSize=10
 * <p>
 * 那么RocketMQ客户端会一次从Broker处拉取100条到本地缓存, 然后一次取出10条, 循环这10条消息, 一条一条回调业务消费者,
 * <p>
 * 如果这10条中前9条都消费成功了, 最后一条抛异常了, 那么下次重试的话就是这10条都要重试一遍, 所以这种场景下有必要对消息做幂等性处理
 *
 * <p>
 * 需要做消息幂等的场景, 建议消息发送端将一个业务唯一的值设置到MessageConst.PROPERTY_KEYS消息头里面,
 * 如果没有设置, 默认fallback到 (bornTimestamp + 消息内容) DE md5哈希, 以极高概率做到消息的唯一性
 * <p/>
 * Copyright: Copyright (c) 2026-02-13 10:51
 * <p/>
 * Company: Sexy Uncle Inc.
 * <p/>
 
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RocketMQ5xIdempotent {
	
	/**
	 * 消息唯一性标识字段, 如果没有指定, 默认会先取消息头 "UNIQ_KEY",
	 * <p>
	 * 如果存在, 就用这个头作为控制幂等性的唯一标识;
	 * <p>
	 * 如果不存在, 那么fallback到 bornTimestamp + 消息内容, 以极高概率做到消息的唯一性
	 * <p>
	 * 但是对消息唯一性有极致要求的场景, 建议发送的时候代码业务自行生成一个唯一标识, 然后设置到消息头中, 以此作为控制幂等性的唯一标识
	 * @return
	 */
	String key() default "";
}