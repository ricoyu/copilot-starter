package com.awesomecopilot.boot.rocketmq5x.aspect;

import com.awesomecopilot.boot.rocketmq5x.annotation.RocketMQ5xIdempotent;
import com.awesomecopilot.cache.JedisUtils.SET;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.MessageExt;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static com.awesomecopilot.boot.rocketmq5x.constants.RocketMQ5xConstants.KEY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * @RocketMQ5xIdempotent AOP 切面类, 用于判断消息是否重复消费了
 * <p/>
 * Copyright: Copyright (c) 2026-02-20 18:20
 * <p/>
 * Company: Sexy Uncle Inc.
 * <p/>
 
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
@Aspect
public class RocketMQ5xIdempotentAspect {
	
	private static final Logger log = LoggerFactory.getLogger(RocketMQ5xIdempotentAspect.class);
	
	@Around("@annotation(idempotent)")
	public Object arount(ProceedingJoinPoint joinPoint, RocketMQ5xIdempotent idempotent) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		//拿到方法的第一个参数
		Object[] args = joinPoint.getArgs();
		/*
		 * 方法签名不符合xxx(MessageExt msg)即不拦截
		 */
		if (args == null || args.length == 0) {
			try {
				return joinPoint.proceed();
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		}
		
		Object arg = args[0];
		//只支持MessageExt类型参数的消费回调方法
		if (!(arg instanceof MessageExt msg)) {
			log.warn("@Idempotent 只能用于 MessageExt 类型的参数");
			//return null;
			try {
				return joinPoint.proceed();
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		}
		
		MessageExt msgExt = (MessageExt)arg;
		String key = idempotent.key();
		String uniqueValue = getDefaultDedupKey(key, msgExt);
		//唯一标识为空则不做幂等性处理了
		if (isBlank(uniqueValue)) {
			try {
				return joinPoint.proceed();
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		}
		
		long addCount = SET.sadd(KEY, uniqueValue);
		String content = new String(msgExt.getBody(), UTF_8);
		//添加成功了就表示这条消息还没有消费过
		if (addCount == 1) {
			try {
				log.info("消息唯一标识: {}, 内容: {}, 将被正常消费", uniqueValue, content);
				return joinPoint.proceed();
			} catch (Throwable e) {
				//消费失败了删除, 避免下次被判定为重复消费
				long count = SET.srem(KEY, uniqueValue);
				throw new RuntimeException(e);
			}
		} else {
			//没添加进去就是消费过了, 打印一条log
			log.info("唯一标识: {} 的消息已经处理过了, 内容为: {}, 将被幂等性处理", uniqueValue, content);
			return null;
		}
	}
	
	private String getDefaultDedupKey(String key, MessageExt msg) {
		// 优先级 1: 注解指定的业务 key（用户自定义，最可靠）
		if (StringUtils.isNotBlank(key)) {
			String custom = msg.getUserProperty(key);
			if (StringUtils.isNotBlank(custom)) {
				return "custom:" + custom;
			}
		}
		
		// 优先级 2: 官方推荐的业务 keys（如果生产端设置了）, 对应的是发送端message.setKeys("key" + i);设置的值
		String keys = msg.getKeys();
		if (StringUtils.isNotBlank(keys)) {
			return "keys:" + keys;
		}
		
		// 默认 fallback: bornTimestamp + body 的 MD5
		try {
			String prefix = msg.getBornTimestamp() + "|";
			byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
			byte[] bodyBytes = msg.getBody();
			
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(prefixBytes);
			md.update(bodyBytes);
			
			byte[] digest = md.digest();
			String md5Hex = Hex.encodeHexString(digest);  // 32 字符
			
			return "md5:" + md5Hex;
		} catch (Exception e) {
			// 极罕见异常，降级到null, 就不做消息幂等性了
			log.warn("MD5 计算失败，使用降级 key", e);
			return null;
		}
	}
}