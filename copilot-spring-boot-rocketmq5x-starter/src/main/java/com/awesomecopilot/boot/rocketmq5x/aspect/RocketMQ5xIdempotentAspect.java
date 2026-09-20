package com.awesomecopilot.boot.rocketmq5x.aspect;

import com.awesomecopilot.boot.rocketmq5x.annotation.RocketMQ5xIdempotent;
import com.awesomecopilot.boot.rocketmq5x.constants.RocketMQ5xConstants;
import com.awesomecopilot.cache.JedisUtils;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.exceptions.JedisException;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * @RocketMQ5xIdempotent AOP 切面类, 用于判断消息是否重复消费了.
 * <p>
 * 判重令牌存储: 每个唯一标识一个独立 Redis key（{@code rocketmq:idempotent:{uniqueValue}}），
 * setnx+过期时间原子写入（JedisUtils.setnx 的 lua 实现），TTL 到期自动清理——
 * 不再往一个永不过期、全局共享的 SET 里 sadd（该写法随消息量单调增长，且幂等窗口不可配置）.
 * <p>
 * 唯一标识取值顺序: 注解 key 指定的用户属性 → 内置 UNIQ_KEY 属性 → msg.getMsgId()。
 * 不使用业务侧 msg.getKeys()——生产端常有意让多条消息共用同一个 keys(如同一订单号)，
 * 用它判重会把后到的合法消息误当重复消费直接确认丢弃。各级取值的稳定性（对依赖源码实测后写明）：
 * UNIQ_KEY 是发送端客户端生成的属性，消费重试链路复制原属性、重投保持不变——它才是覆盖
 * "投递重试导致重复"这一主场景的层级；msgId 由 broker 按"存储主机+commitlog偏移"计算
 * （rocketmq-common 的 MessageDecoder 收到消息时无条件 setMsgId），消息重新写入重试 topic
 * 会产生新值——msgId 一级只覆盖"消费进程退出后从同一队列位点重新拉到同一条物理消息"，
 * 对重投不保证。对唯一性有强要求的业务用注解 key 显式指定逐条唯一的业务号属性.
 * <p>
 * 适用方法形态: 第一参数 MessageExt、返回 void 的消费方法. rocketmq-spring 的
 * RocketMQListener.onMessage 即 void, 容器 handleMessage 丢弃监听方法返回值、只按
 * "有没有抛异常"决定 RECONSUME_LATER/CONSUME_SUCCESS——重复消息时本切面不抛异常直接
 * 返回, 容器即确认跳过, 语义正确. 非 void 方法(如应答式 RocketMQReplyListener, 其返回值
 * 要回写给请求方)首次调用即抛 IllegalStateException 点名方法: 切面无法替它构造正确应答,
 * 放行或返回 null 都会造成更隐蔽的错误.
 * <p>
 * Redis 故障策略: 默认 fail-closed（占位失败抛回让容器重试, 宁可延迟消费不做无保护消费）；
 * 配置 copilot.rocket5x.idempotent.fail-open-on-redis-error=true 后放行消费（可用性优先,
 * 故障窗口内幂等保护暂停）。仅捕获 JedisException 族——接缝里代码自身的错误照常抛出暴露.
 * <p>
 * 已知窗口（未修复, 如实声明）: 业务执行失败后释放令牌又遇 Redis 故障时, 该消息的重投会
 * 命中残留令牌、被按"重复"确认跳过——消息从未成功处理却被确认, 且因确认成功不再累加重试、
 * 不会进死信队列, 只能靠释放失败的 error 日志人工补处理. 彻底解法需要"占位/完成"两态令牌
 * 并配租约语义（否则 consumeTimeout 超时重投时, 处理中的消息会被第二个线程同时执行）,
 * 属单独设计, 未随本修复引入.
 * <p/>
 * Copyright: Copyright (c) 2026-02-20 18:20
 * <p/>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
@Aspect
public class RocketMQ5xIdempotentAspect {
	
	private static final Logger log = LoggerFactory.getLogger(RocketMQ5xIdempotentAspect.class);
	
	/** 每个幂等令牌一个独立 key 的前缀 */
	static final String KEY_PREFIX = "rocketmq:idempotent:";
	
	/**
	 * 默认幂等窗口 6 小时. 依据: 客户端重试延迟档 = 3+reconsumeTimes(level), broker 默认
	 * messageDelayLevel 表下 16 次重试全部跑完约 4h46m, 取 21600s 留余量. 重试间隔在 broker
	 * 侧可配置, 本值只是默认配置下的折中, 非常规配置请显式调大 copilot.rocket5x.idempotent.ttl-seconds.
	 */
	public static final long DEFAULT_TTL_SECONDS = 6L * 3600;
	
	private final long ttlSeconds;
	private final boolean failOpenOnRedisError;
	
	public RocketMQ5xIdempotentAspect() {
		this(DEFAULT_TTL_SECONDS, false);
	}
	
	public RocketMQ5xIdempotentAspect(long ttlSeconds, boolean failOpenOnRedisError) {
		if (ttlSeconds <= 0) {
			//0/负数不再悄悄改回默认值——调用方必须知道实际生效的窗口
			log.warn("ttlSeconds={} 不是正数, 按默认 {} 秒处理", ttlSeconds, DEFAULT_TTL_SECONDS);
			ttlSeconds = DEFAULT_TTL_SECONDS;
		}
		this.ttlSeconds = ttlSeconds;
		this.failOpenOnRedisError = failOpenOnRedisError;
	}
	
	@Around("@annotation(idempotent)")
	public Object arount(ProceedingJoinPoint joinPoint, RocketMQ5xIdempotent idempotent) throws Throwable {
		Object[] args = joinPoint.getArgs();
		/*
		 * 方法签名不符合xxx(MessageExt msg)即不拦截
		 */
		if (args == null || args.length == 0 || !(args[0] instanceof MessageExt msg)) {
			log.warn("@RocketMQ5xIdempotent 只能用于第一参数为 MessageExt 类型的消费方法, 本次直接放行执行");
			return joinPoint.proceed();
		}
		
		//只支持 void 消费方法(理由见类注释"适用方法形态"): 非 void 首次调用即点名报错, 不留歧义运行
		Class<?> returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();
		if (returnType != void.class) {
			throw new IllegalStateException("@RocketMQ5xIdempotent 只能用于返回 void 的消费方法(rocketmq-spring 容器"
				+ "不读监听方法返回值; 应答式 RocketMQReplyListener 的返回值切面无法代为构造), 当前方法 "
				+ joinPoint.getSignature().getName() + " 返回: " + returnType.getName());
		}
		
		String uniqueValue = resolveUniqueValue(idempotent.key(), msg);
		//唯一标识为空则不做幂等性处理了
		if (isBlank(uniqueValue)) {
			return joinPoint.proceed();
		}
		
		boolean firstSeen;
		try {
			firstSeen = acquireToken(uniqueValue, ttlSeconds);
		} catch (JedisException redisEx) {
			if (failOpenOnRedisError) {
				log.error("Redis 不可用, 幂等保护暂停, 本次放行消费(配置了 fail-open): uniqueValue={}",
					uniqueValue, redisEx);
				return joinPoint.proceed();
			}
			//fail-closed: 抛回使容器稍后重试, 宁可延迟消费也不做无幂等保护的消费
			log.error("Redis 不可用, 按 fail-closed 策略抛回使消息重试: uniqueValue={}", uniqueValue, redisEx);
			throw redisEx;
		}
		
		if (firstSeen) {
			//日志只带唯一标识, 不打消息内容(防 PII/金额进日志, 防消息量大时日志膨胀)
			log.info("消息唯一标识: {} 首次消费, 将被正常处理", uniqueValue);
			try {
				return joinPoint.proceed();
			} catch (Throwable bizEx) {
				//消费失败释放令牌, 允许重试时被再次消费; 原异常不包装、原样抛出,
				//调用方/容器的异常类型判断保持有效
				try {
					releaseToken(uniqueValue);
				} catch (Exception releaseEx) {
					//后果见类注释"已知窗口": 该消息重投会被按重复直接确认丢弃、不进死信——
					//必须能按 uniqueValue 从这条日志定位并人工补处理
					log.error("消费失败后释放幂等令牌失败! 该消息重试时会被当作重复直接确认丢弃(不报错、不进死信), "
						+ "请按 uniqueValue 人工补处理: uniqueValue={}", uniqueValue, releaseEx);
				}
				throw bizEx;
			}
		}
		
		//void 方法: 不抛异常返回即被容器确认跳过(返回值无消费方)
		log.info("唯一标识: {} 的消息已处理过, 本次按重复消费跳过", uniqueValue);
		return null;
	}
	
	/**
	 * 解析判重唯一标识. 优先级: 注解指定 key 的用户属性 → 内置 UNIQ_KEY 属性 → msgId.
	 * 刻意不使用 msg.getKeys()（业务键, 常有意被多条消息共用, 见类注释）;
	 * 注解 key 填 "KEYS" 等于把被禁止的业务 keys 判重重新打开, 告警并忽略, 继续按 UNIQ_KEY 走.
	 */
	static String resolveUniqueValue(String annotationKey, MessageExt msg) {
		if (!isBlank(annotationKey)) {
			if (MessageConst.PROPERTY_KEYS.equals(annotationKey)) {
				log.warn("@RocketMQ5xIdempotent(key=\"KEYS\") 会把业务 keys 当幂等标识——keys 常有多条消息共用, "
					+ "会误丢合法消息; 已忽略该配置改用 UNIQ_KEY, 请换成逐条唯一的业务号用户属性");
			} else {
				String custom = msg.getUserProperty(annotationKey);
				if (!isBlank(custom)) {
					return "custom:" + custom;
				}
			}
		}
		//RocketMQ 客户端发送时自动写入的内置唯一键(MessageClientIDSetter), 重试链路复制属性保持不变
		String uniqKey = msg.getUserProperty(RocketMQ5xConstants.UNIQ_KEY);
		if (!isBlank(uniqKey)) {
			return "uniqKey:" + uniqKey;
		}
		if (!isBlank(msg.getMsgId())) {
			//broker 重投会产生新 msgId, 此级只覆盖同位点重复拉取, 见类注释
			return "msgId:" + msg.getMsgId();
		}
		//不打 msg.toString(): MessageExt.toString 含全部属性与消息体字节, 会把业务内容带进日志
		log.error("消息无法解析出唯一标识(UNIQ_KEY 与 msgId 均缺失), 本次不做幂等保护, topic={}, queueId={}, bornTimestamp={}, reconsumeTimes={}",
			msg.getTopic(), msg.getQueueId(), msg.getBornTimestamp(), msg.getReconsumeTimes());
		return null;
	}
	
	static String redisKey(String uniqueValue) {
		return KEY_PREFIX + uniqueValue;
	}
	
	/** setnx+过期原子占位: true=首次(拿到令牌), false=已存在(重复消费). 测试覆写接缝隔离 Redis */
	protected boolean acquireToken(String uniqueValue, long ttlSeconds) {
		return JedisUtils.setnx(redisKey(uniqueValue), "1", ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
	}
	
	/** 消费失败释放令牌. 测试覆写接缝隔离 Redis */
	protected void releaseToken(String uniqueValue) {
		JedisUtils.del(redisKey(uniqueValue));
	}
}
