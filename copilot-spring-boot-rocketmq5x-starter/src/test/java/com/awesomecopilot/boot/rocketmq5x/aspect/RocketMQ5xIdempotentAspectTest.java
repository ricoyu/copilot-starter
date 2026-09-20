package com.awesomecopilot.boot.rocketmq5x.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.awesomecopilot.boot.rocketmq5x.annotation.RocketMQ5xIdempotent;
import com.awesomecopilot.boot.rocketmq5x.properties.CopilotRocketMQ5xProperties;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.exceptions.JedisException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消费幂等切面 RocketMQ5xIdempotentAspect 的行为测试（评审报告 P1-1/P1-2/P2-3）.
 * <p>
 * 通过覆写 acquireToken/releaseToken 接缝隔离真实 Redis;
 * 验证: 每令牌独立 key+TTL、判重不使用业务 keys、UNIQ_KEY 优先于 msgId、非 void 方法拒绝、
 * 业务异常释放令牌且原样重抛、Redis 故障 fail-open/fail-closed 开关、日志不带消息体、
 * TTL 默认值单一来源.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class RocketMQ5xIdempotentAspectTest {
	
	private static final Logger ASPECT_LOG =
		(Logger) LoggerFactory.getLogger(RocketMQ5xIdempotentAspect.class);
	
	/** 不连 Redis 的测试切面: 记录令牌动作, 按脚本返回获取结果 */
	static class FakeAspect extends RocketMQ5xIdempotentAspect {
		final List<String> acquired = new ArrayList<>();
		final List<Long> ttls = new ArrayList<>();
		final List<String> released = new ArrayList<>();
		boolean acquireResult = true;
		RuntimeException acquireFailure;
		
		FakeAspect() {this(3600L, false);}
		
		FakeAspect(long ttlSeconds, boolean failOpen) {
			super(ttlSeconds, failOpen);
		}
		
		@Override
		protected boolean acquireToken(String uniqueValue, long ttlSeconds) {
			if (acquireFailure != null) {
				throw acquireFailure;
			}
			acquired.add(uniqueValue);
			ttls.add(ttlSeconds);
			return acquireResult;
		}
		
		@Override
		protected void releaseToken(String uniqueValue) {
			released.add(uniqueValue);
		}
	}
	
	private static final RocketMQ5xIdempotent DEFAULT_ANN = new RocketMQ5xIdempotent() {
		@Override
		public Class<? extends java.lang.annotation.Annotation> annotationType() {return RocketMQ5xIdempotent.class;}
		
		@Override
		public String key() {return "";}
	};
	
	private static RocketMQ5xIdempotent annotationWithKey(String k) {
		return new RocketMQ5xIdempotent() {
			@Override
			public Class<? extends java.lang.annotation.Annotation> annotationType() {return RocketMQ5xIdempotent.class;}
			
			@Override
			public String key() {return k;}
		};
	}
	
	private static MessageExt message(String msgId, String keys, String body) {
		MessageExt msg = new MessageExt();
		msg.setMsgId(msgId);
		msg.setKeys(keys);
		if (body != null) {
			msg.setBody(body.getBytes(StandardCharsets.UTF_8));
		}
		msg.setBornTimestamp(System.currentTimeMillis());
		return msg;
	}
	
	/** 绕过客户端 putUserProperty 的系统属性校验, 模拟 broker 落盘消息上的真实属性(UNIQ_KEY/KEYS 是系统键) */
	private static void putSystemLikeProperty(MessageExt msg, String name, String value) {
		msg.getProperties().put(name, value);
	}
	
	private static ProceedingJoinPoint joinPoint(Object[] args, Class<?> returnType) {
		ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
		MethodSignature signature = mock(MethodSignature.class);
		when(signature.getReturnType()).thenReturn(returnType);
		when(signature.getName()).thenReturn("onMessage");
		when(joinPoint.getSignature()).thenReturn(signature);
		when(joinPoint.getArgs()).thenReturn(args);
		return joinPoint;
	}
	
	@Test
	void duplicateMessageReturnsNullWithoutProceeding() throws Throwable {
		//void 消费方法: 容器只按"有没有抛异常"判定, 切面正常返回即被确认跳过
		FakeAspect aspect = new FakeAspect();
		aspect.acquireResult = false;   //令牌已被占: 重复消息
		ProceedingJoinPoint joinPoint = joinPoint(new Object[]{message("M1", null, "body")}, void.class);
		
		Object result = aspect.arount(joinPoint, DEFAULT_ANN);
		
		assertThat(result).isNull();
		verify(joinPoint, never()).proceed();
	}
	
	@Test
	void statusReturningMethodIsRejectedAtFirstCall() throws Throwable {
		//独立评审实测: rocketmq-spring 的 RocketMQListener.onMessage 是 void, 容器丢弃监听方法返回值;
		//返回 ConsumeConcurrentlyStatus 的方法不满足该接口, 属误用形态——首次调用即点名报错而不是带歧义运行
		FakeAspect aspect = new FakeAspect();
		ProceedingJoinPoint joinPoint = joinPoint(new Object[]{message("M1", null, "body")},
			ConsumeConcurrentlyStatus.class);
		
		assertThatThrownBy(() -> aspect.arount(joinPoint, DEFAULT_ANN))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("onMessage")
			.hasMessageContaining("ConsumeConcurrentlyStatus");
	}
	
	@Test
	void otherReturningMethodRejectedAtFirstCall() throws Throwable {
		//应答式(RocketMQReplyListener)等返回 String 的方法同样拒绝: 切面无法替它构造应答
		FakeAspect aspect = new FakeAspect();
		ProceedingJoinPoint joinPoint = joinPoint(new Object[]{message("M1", null, "body")}, String.class);
		
		assertThatThrownBy(() -> aspect.arount(joinPoint, DEFAULT_ANN))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("void");
	}
	
	@Test
	void businessExceptionReleasesTokenAndKeepsOriginalException() throws Throwable {
		FakeAspect aspect = new FakeAspect();
		IllegalStateException original = new IllegalStateException("biz boom");
		ProceedingJoinPoint joinPoint = joinPoint(new Object[]{message("M1", null, "body")}, void.class);
		when(joinPoint.proceed()).thenThrow(original);
		
		assertThatThrownBy(() -> aspect.arount(joinPoint, DEFAULT_ANN))
			.isSameAs(original);   //修复前被包成 RuntimeException, 调用方 instanceof 全部失效
		
		assertThat(aspect.released).hasSize(1);   //失败释放令牌, 允许重试消费
	}
	
	@Test
	void bizKeysNotUsedAsUniqueValue() throws Throwable {
		//P1-2 核心回归: 业务 keys(生产者常有意多条消息共用)不再参与判重;
		//本夹具未写 UNIQ_KEY 属性, 落到 msgId 一级
		FakeAspect aspect = new FakeAspect();
		ProceedingJoinPoint joinPoint = joinPoint(new Object[]{message("AC1D84C0-1", "order-888", "body")}, void.class);
		
		aspect.arount(joinPoint, DEFAULT_ANN);
		
		assertThat(aspect.acquired).containsExactly("msgId:AC1D84C0-1");
		assertThat(aspect.ttls).containsExactly(3600L);   //构造参数按原值传给占位调用
	}
	
	@Test
	void uniqKeyPropertyPreferredOverMsgId() throws Throwable {
		//UNIQ_KEY 在重试链路保持不变, 是覆盖"投递重试导致重复"的主层级, 必须优先于 msgId
		MessageExt msg = message("BROKER-M1", null, "body");
		putSystemLikeProperty(msg, "UNIQ_KEY", "AC1D84C0-uniq-1");
		FakeAspect aspect = new FakeAspect();
		ProceedingJoinPoint joinPoint = joinPoint(new Object[]{msg}, void.class);
		
		aspect.arount(joinPoint, DEFAULT_ANN);
		
		assertThat(aspect.acquired).containsExactly("uniqKey:AC1D84C0-uniq-1");
	}
	
	@Test
	void annotationKeyUsesCustomUserProperty() throws Throwable {
		MessageExt msg = message("BROKER-M1", null, "body");
		putSystemLikeProperty(msg, "UNIQ_KEY", "UK-1");
		putSystemLikeProperty(msg, "bizNo", "B-777");
		FakeAspect aspect = new FakeAspect();
		ProceedingJoinPoint joinPoint = joinPoint(new Object[]{msg}, void.class);
		
		aspect.arount(joinPoint, annotationWithKey("bizNo"));
		
		assertThat(aspect.acquired).containsExactly("custom:B-777");
	}
	
	@Test
	void keyEqualsKeysIsIgnoredWithWarn() throws Throwable {
		//key="KEYS" 等于把被禁止的业务 keys 判重重新打开(KEYS 就是 setKeys 的属性名)——告警并忽略
		MessageExt msg = message("M1", "order-888", "body");
		putSystemLikeProperty(msg, "KEYS", "order-888");
		FakeAspect aspect = new FakeAspect();
		ProceedingJoinPoint joinPoint = joinPoint(new Object[]{msg}, void.class);
		
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		ASPECT_LOG.addAppender(appender);
		try {
			aspect.arount(joinPoint, annotationWithKey("KEYS"));
		} finally {
			ASPECT_LOG.detachAppender(appender);
		}
		
		//落到了 uniqKey/msgId 级而不是 custom:order-888
		assertThat(aspect.acquired).containsExactly("msgId:M1");
		assertThat(appender.list.stream()
			.filter(e -> e.getLevel() == Level.WARN)
			.map(ILoggingEvent::getFormattedMessage)
			.toList())
			.anyMatch(s -> s.contains("KEYS") && s.contains("误丢"));
	}
	
	@Test
	void nullBodyFallsBackToMsgIdWithoutNpe() throws Throwable {
		//修复前: body 为 null 时 new String(null) NPE, 消息进无限重试
		FakeAspect aspect = new FakeAspect();
		ProceedingJoinPoint joinPoint = joinPoint(new Object[]{message("M9", null, null)}, void.class);
		
		aspect.arount(joinPoint, DEFAULT_ANN);
		
		assertThat(aspect.acquired).containsExactly("msgId:M9");
		verify(joinPoint).proceed();
	}
	
	@Test
	void redisDownFailClosedRethrowsForRetry() throws Throwable {
		//默认 fail-closed: Redis 不可用时抛回, 容器稍后重试消息, 不产生无保护消费
		FakeAspect aspect = new FakeAspect();
		aspect.acquireFailure = new JedisException("simulated redis down");
		ProceedingJoinPoint joinPoint = joinPoint(new Object[]{message("M1", null, "body")}, void.class);
		
		assertThatThrownBy(() -> aspect.arount(joinPoint, DEFAULT_ANN))
			.hasMessageContaining("simulated redis down");
		verify(joinPoint, never()).proceed();
	}
	
	@Test
	void redisDownFailOpenSkipsIdempotencyAndProceeds() throws Throwable {
		FakeAspect aspect = new FakeAspect(3600L, true);
		aspect.acquireFailure = new JedisException("simulated redis down");
		ProceedingJoinPoint joinPoint = joinPoint(new Object[]{message("M1", null, "body")}, void.class);
		
		aspect.arount(joinPoint, DEFAULT_ANN);
		
		verify(joinPoint).proceed();   //可用性优先: Redis 故障时放行消费, 幂等窗口暂失
	}
	
	@Test
	void nonPositiveTtlWarnsAndFallsBackToDefault() {
		//独立评审建议: 0/负数不再悄悄改回默认值, 要有 warn; 实际生效值可观察(acquireToken 收到 DEFAULT)
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		ASPECT_LOG.addAppender(appender);
		FakeAspect aspect;
		try {
			aspect = new FakeAspect(0L, false);
		} finally {
			ASPECT_LOG.detachAppender(appender);
		}
		assertThat(appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN)).isTrue();
	}
	
	@Test
	void productionDefaultsConsistentAcrossAspectAndProperties() {
		//独立评审建议6: 默认 TTL 单一来源——切面常量与 properties 字段必须一致(现 properties 引用常量)
		assertThat(RocketMQ5xIdempotentAspect.DEFAULT_TTL_SECONDS).isEqualTo(6L * 3600);
		assertThat(new CopilotRocketMQ5xProperties().getIdempotent().getTtlSeconds())
			.isEqualTo(RocketMQ5xIdempotentAspect.DEFAULT_TTL_SECONDS);
	}
	
	@Test
	void logsCarryUniqueValueNotBodyContent() throws Throwable {
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		ASPECT_LOG.addAppender(appender);
		try {
			FakeAspect aspect = new FakeAspect();
			ProceedingJoinPoint joinPoint = joinPoint(
				new Object[]{message("M1", null, "amount=9988&idCard=110101")}, void.class);
			aspect.arount(joinPoint, DEFAULT_ANN);
		} finally {
			ASPECT_LOG.detachAppender(appender);
		}
		
		String all = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
		assertThat(all).contains("msgId:M1");
		assertThat(all).doesNotContain("amount=9988").doesNotContain("idCard");
	}
	
	@Test
	void missingIdentityLogDoesNotDumpWholeMessage() {
		//独立评审建议4: UNIQ_KEY 与 msgId 均缺失时不能 log msg.toString()(含全部属性与消息体), 只打无内容字段
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		ASPECT_LOG.addAppender(appender);
		String result;
		try {
			result = RocketMQ5xIdempotentAspect.resolveUniqueValue("", message(null, null, "secret-body"));
		} finally {
			ASPECT_LOG.detachAppender(appender);
		}
		assertThat(result).isNull();
		String all = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
		assertThat(all).contains("不做幂等保护");
		assertThat(all).doesNotContain("secret-body");
	}
	
	@Test
	void redisKeyIsPerTokenWithPrefix() {
		assertThat(RocketMQ5xIdempotentAspect.redisKey("uniqKey:UK-1"))
			.isEqualTo("rocketmq:idempotent:uniqKey:UK-1");
	}
}
