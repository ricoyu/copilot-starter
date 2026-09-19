package com.awesomecopilot.boot.annotation.processor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.awesomecopilot.boot.annotation.RedisListener;
import com.awesomecopilot.cache.listeners.MessageListener;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisListenerProcessor 消息回调的行为测试（评审报告 P1-2）.
 * <p>
 * 验证的是注册后回调本身的行为, 不需要真实 Redis:
 * <ol>
 *     <li>监听方法抛异常时, 异常不允许传播出回调——psubscribe 的回调在 Jedis 套接字读线程上同步执行,
 *     异常抛出会中断 JedisPubSub 事件循环, 该连接上的全部 pattern 订阅一起失效;</li>
 *     <li>异常要被记录到日志(含 bean 与方法信息);</li>
 *     <li>protected 监听方法注册时已 setAccessible, 调用能到达方法体;</li>
 *     <li>单参数方法只收消息内容, 双参数方法收 channel+消息(原有语义回归保护).</li>
 * </ol>
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class RedisListenerProcessorTest {

	static class ListenerBean {
		final List<String> invoked = new ArrayList<>();

		@RedisListener(channels = "c1")
		public void failing(String message) {
			throw new IllegalStateException("boom: " + message);
		}

		@RedisListener(channels = "c1")
		protected void protectedListener(String channel, String message) {
			invoked.add(channel + "|" + message);
		}

		@RedisListener(channels = "c1")
		public void normalOneArg(String message) {
			invoked.add(message);
		}

		@RedisListener(channels = "c1", messagePattern = "\\d+")
		public void digitsOnly(String message) {
			invoked.add(message);
		}

		@RedisListener(channels = "c1", messagePattern = "[")
		public void badPatternListener(String message) {
			invoked.add(message);
		}
	}

	private static RedisListener annotationOf(Method method) {
		return AnnotationUtils.findAnnotation(method, RedisListener.class);
	}

	@Test
	void exceptionThrownByListenerMethodMustNotPropagateOutOfCallback() throws Exception {
		ListenerBean bean = new ListenerBean();
		Method method = ListenerBean.class.getDeclaredMethod("failing", String.class);

		MessageListener callback = RedisListenerProcessor.createMessageListener(bean, method, annotationOf(method));

		// 修复前: RuntimeException 会传播出 onMessage; 修复后: 不向调用方抛出任何异常
		try {
			callback.onMessage("c1", "hello");
		} catch (Throwable t) {
			throw new AssertionError("监听方法的异常不应传播出回调, 否则 psubscribe 连接上的订阅会全部失效", t);
		}
	}

	@Test
	void exceptionIsLoggedWithBeanAndMethodInfo() throws Exception {
		ListenerBean bean = new ListenerBean();
		Method method = ListenerBean.class.getDeclaredMethod("failing", String.class);
		MessageListener callback = RedisListenerProcessor.createMessageListener(bean, method, annotationOf(method));

		ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
			LoggerFactory.getLogger(RedisListenerProcessor.class);
		Level previousLevel = logger.getLevel();
		logger.setLevel(Level.DEBUG);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			callback.onMessage("c1", "hello");
		} finally {
			logger.detachAppender(appender);
			logger.setLevel(previousLevel);
		}

		List<ILoggingEvent> errors = new ArrayList<>();
		for (ILoggingEvent event : appender.list) {
			if (event.getLevel() == Level.ERROR) {
				errors.add(event);
			}
		}
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0).getFormattedMessage())
			.contains("Invoke @RedisListener annotation method failed")
			.contains("failing");
		// 原始异常链必须保留在日志里, 否则线上无法定位
		assertThat(errors.get(0).getThrowableProxy()).isNotNull();
	}

	@Test
	void protectedListenerMethodIsInvoked() throws Exception {
		ListenerBean bean = new ListenerBean();
		Method method = ListenerBean.class.getDeclaredMethod("protectedListener", String.class, String.class);

		MessageListener callback = RedisListenerProcessor.createMessageListener(bean, method, annotationOf(method));
		callback.onMessage("c1", "msg1");

		// 修复前: 首条消息即 IllegalAccessException; 修复后正常进入方法体
		assertThat(bean.invoked).containsExactly("c1|msg1");
	}

	@Test
	void oneArgMethodReceivesMessageOnly() throws Exception {
		ListenerBean bean = new ListenerBean();
		Method method = ListenerBean.class.getDeclaredMethod("normalOneArg", String.class);
		MessageListener callback = RedisListenerProcessor.createMessageListener(bean, method, annotationOf(method));

		callback.onMessage("c1", "m2");

		assertThat(bean.invoked).containsExactly("m2");
	}

	@Test
	void messageNotMatchingPatternIsNotConsumed() throws Exception {
		ListenerBean bean = new ListenerBean();
		Method method = ListenerBean.class.getDeclaredMethod("digitsOnly", String.class);
		MessageListener callback = RedisListenerProcessor.createMessageListener(bean, method, annotationOf(method));

		callback.onMessage("c1", "abc");
		assertThat(bean.invoked).isEmpty();

		callback.onMessage("c1", "123");
		assertThat(bean.invoked).containsExactly("123");
	}

	@Test
	void illegalMessagePatternFailsFastAtRegistration() throws Exception {
		ListenerBean bean = new ListenerBean();
		Method method = ListenerBean.class.getDeclaredMethod("badPatternListener", String.class);

		// messagePattern="[" 是非法正则: 注册(启动)阶段就抛带方法名的异常, 而不是留到
		// 首条消息到达时在回调里抛 PatternSyntaxException 去中断 psubscribe 连接
		try {
			RedisListenerProcessor.createMessageListener(bean, method, annotationOf(method));
			throw new AssertionError("非法 messagePattern 应在注册时就抛出 IllegalStateException");
		} catch (IllegalStateException expected) {
			assertThat(expected).hasMessageContaining("badPatternListener").hasMessageContaining("messagePattern");
		}
	}
}
