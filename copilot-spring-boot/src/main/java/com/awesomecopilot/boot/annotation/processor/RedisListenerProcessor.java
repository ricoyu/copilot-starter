package com.awesomecopilot.boot.annotation.processor;

import com.awesomecopilot.boot.annotation.RedisListener;
import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.cache.listeners.MessageListener;
import com.awesomecopilot.common.lang.utils.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * <p>
 * Copyright: (C), 2020-09-10 14:01
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
public class RedisListenerProcessor implements SmartInitializingSingleton {
	
	private static Logger log = LoggerFactory.getLogger(RedisListenerProcessor.class);
	
	@Autowired
	private ApplicationContext applicationContext;
	
	@Override
	public void afterSingletonsInstantiated() {
		/*
		 * 关于重复注册的说明: bean.getClass() 拿到的如果是 CGLIB 代理类, commons-lang 的 ReflectionUtils.doWithMethods
		 * 只扫描代理类自己声明的方法, 不会再向上扫描被代理的目标类(见其 "代理类不找父类" 分支), 因此同一个 @RedisListener
		 * 方法在一个 bean 上只会命中一次. 前提是容器里同一个类只注册一个 bean 实例;
		 * 若代理实例与原始实例被分别注册成两个 bean(历史实测中出现过), 每个实例各订阅一次, 方法仍会被调用两遍——
		 * 该情况靠修正 bean 注册解决, 此处不再做方法级去重.
		 */
		Collection<Object> allBeans = this.applicationContext.getBeansOfType(null, false, false).values();
		allBeans.stream()
				.filter(Objects::nonNull)
				.forEach((bean) -> {
					ReflectionUtils.doWithMethods(bean.getClass(), (method) -> {
						RedisListener annotation = AnnotationUtils.findAnnotation(method, RedisListener.class);
						if (annotation == null) {
							return;
						}
						
						String[] channels = annotation.channels();
						if (channels != null && channels.length > 0) {
							JedisUtils.subscribe(createMessageListener(bean, method, annotation), channels);
						} else {
							String[] channelPatterns = annotation.channelPatterns();
							if (channelPatterns != null && channelPatterns.length > 0) {
								JedisUtils.psubscribe(createMessageListener(bean, method, annotation), channelPatterns);
							}
						}
					});
				});
	}
	
	/**
	 * 为单个 @RedisListener 方法构造消息回调. subscribe 与 psubscribe 共用此逻辑.
	 * <p>
	 * 三个关键点:
	 * <ol>
	 *     <li>注册时就把方法设为可访问, 否则 protected/private 的监听方法收到首条消息就会抛 IllegalAccessException;</li>
	 *     <li>messagePattern 在注册(启动)阶段预编译, 非法正则当场抛 IllegalStateException 并带上方法名,
	 *     应用起不来立刻能看见; 留到消息到达时才抛 PatternSyntaxException 会中断 psubscribe 的事件循环;</li>
	 *     <li>监听方法抛出的任何异常都只记录日志、不向上传播. psubscribe 的回调是在 Jedis 套接字的读线程上同步执行的,
	 *     异常一旦抛出回调, 会中断 JedisPubSub 的事件循环, 该连接上的所有 pattern 订阅一起失效且无感知;
	 *     subscribe 的回调虽然跑在线程池里, 同样没有让单条消息的失败影响后续消费的理由.</li>
	 * </ol>
	 */
	static MessageListener createMessageListener(Object bean, Method method, RedisListener annotation) {
		ReflectionUtils.makeAccessible(method);
		
		//注册期预编译消息过滤正则, 非法配置在启动阶段暴露
		final Pattern pattern;
		String messagePattern = annotation.messagePattern();
		if (isNotBlank(messagePattern)) {
			try {
				pattern = Pattern.compile(messagePattern);
			} catch (PatternSyntaxException e) {
				throw new IllegalStateException("@RedisListener method " + method.getDeclaringClass().getName()
					+ "#" + method.getName() + " 的 messagePattern 不是合法正则: " + messagePattern, e);
			}
		} else {
			pattern = null;
		}
		
		return (channel, message) -> {
			//对消息进行过滤, 只有匹配正则的消息才会被消费
			if (pattern != null && !pattern.matcher(message).matches()) {
				log.info("Message:[{}] does not match pattern {}, message on channel[{}], so will not be consumed", message, messagePattern, channel);
				return;
			}
			try {
				int parameterCount = method.getParameterCount();
				//方法就定义了一个参数的话只传消息内容
				if (parameterCount == 1) {
					method.invoke(bean, message);
				} else {
					//定义了两个参数的话, 第一个参数是channel, 第二个参数是消息内容
					method.invoke(bean, channel, message);
				}
			} catch (Throwable e) {
				//异常绝不传播出回调: psubscribe 回调跑在 Jedis 读线程上, 抛出会中断整条订阅连接.
				//这条消息就此丢失, 日志必须带上 channel 与消息内容, 否则事后无法定位补发哪条消息
				log.error("Invoke @RedisListener annotation method failed! bean:{} method:{}, channel:{}, message:{}",
					bean.getClass(), method.getName(), channel, message, e);
			}
		};
	}
}
