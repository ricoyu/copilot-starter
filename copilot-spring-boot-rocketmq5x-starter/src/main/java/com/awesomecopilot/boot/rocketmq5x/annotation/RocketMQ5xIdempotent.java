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
 * 适用方法形态: 第一参数为 MessageExt、返回 void 的消费方法. 业务异常请原样抛出——
 * 切面会释放幂等令牌后把异常抛回, 容器据此重试; 重复消息记 info 日志后直接确认跳过.
 * <p>
 * 判重取值顺序: key() 指定的用户属性 → 客户端内置 UNIQ_KEY 属性 → msgId.
 * 需要按业务号判重时, 发送端把"逐条消息唯一"的业务号 putUserProperty 进消息属性,
 * 并把属性名填到本注解的 key——不要用 setKeys/KEYS(业务 keys 常被有意设计为多条消息共用,
 * 用它判重会把后到的合法消息误当重复丢弃).
 * <p/>
 * Copyright: Copyright (c) 2026-02-13 10:51
 * <p/>
 * Company: Sexy Uncle Inc.
 * <p/>
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RocketMQ5xIdempotent {
	
	/**
	 * 判重取值优先使用的消息用户属性名(发送端 putUserProperty 写入, 值必须逐条消息唯一).
	 * <p>
	 * 未指定或消息里取不到该属性时, 依次回退到客户端自动写入的内置属性 "UNIQ_KEY"
	 * (发送端客户端生成、重试链路保持不变, 能覆盖"投递重试导致重复"的主场景),
	 * 再回退到 msgId(注意: broker 重投会生成新 msgId, 该级只覆盖同位点重复拉取).
	 * <p>
	 * 填 "KEYS" 会被拒绝并告警(等同被禁止的业务 keys 判重), 继续按 UNIQ_KEY 处理.
	 *
	 * @return 用户属性名, 默认空
	 */
	String key() default "";
}
