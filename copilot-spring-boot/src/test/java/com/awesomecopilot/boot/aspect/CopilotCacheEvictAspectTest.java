package com.awesomecopilot.boot.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.awesomecopilot.boot.annotation.CacheEvict;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 双删切面 CopilotCacheEvictAspect 的行为测试（评审报告 P1-3/P1-4/P1-5）.
 * <p>
 * 只测不依赖 Redis 的部分: 注解解析、key 解析、延迟计算、调度队列限流.
 * 真正执行 JedisUtils.del 的链路通过覆写 evictKeys 接缝隔离(测试环境没有 Redis 配置,
 * 加载 JedisUtils 会走不通), 该链路行为见修复记录中的验证边界说明.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class CopilotCacheEvictAspectTest {
	
	private static final Logger ASPECT_LOG =
		(Logger) LoggerFactory.getLogger(CopilotCacheEvictAspect.class);
	
	public interface OrderService {
		void update(String orderNo, OrderDto dto);
	}
	
	public static class OrderDto {
		private final Long brandId;
		
		public OrderDto(Long brandId) {this.brandId = brandId;}
		
		public Long getBrandId() {return brandId;}
	}
	
	//注解只写在实现类方法上(最常见的写法)
	public static class OrderServiceImpl implements OrderService {
		@CacheEvict(keys = {"category_brands_#{#dto.brandId}", "plain_key"})
		@Override
		public void update(String orderNo, OrderDto dto) {
		}
		
		@CacheEvict(keys = {"no_param_key"}, evictDelaySeconds = 3)
		public void zeroArg() {
		}
		
		public void notAnnotated() {
		}
	}
	
	/**
	 * 记录本应删除的 key、不真正连 Redis 的测试用切面
	 */
	static class RecordingAspect extends CopilotCacheEvictAspect {
		final List<String> deleted = Collections.synchronizedList(new ArrayList<>());
		final List<String> stages = Collections.synchronizedList(new ArrayList<>());
		final int maxPending;
		final long defaultDelay;
		
		RecordingAspect() {this(1L, 1000);}
		
		RecordingAspect(long defaultDelaySeconds, int maxPendingTasks) {
			super(defaultDelaySeconds, maxPendingTasks);
			this.defaultDelay = defaultDelaySeconds;
			this.maxPending = maxPendingTasks;
		}
		
		@Override
		void evictKeys(String stage, List<String> keys) {
			stages.add(stage);
			deleted.addAll(keys);
		}
	}
	
	private static JoinPoint mockUpdateJoinPoint() throws Exception {
		MethodSignature signature = mock(MethodSignature.class);
		when(signature.getMethod()).thenReturn(
			OrderServiceImpl.class.getDeclaredMethod("update", String.class, OrderDto.class));
		when(signature.getParameterNames()).thenReturn(new String[]{"orderNo", "dto"});
		JoinPoint joinPoint = mock(JoinPoint.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(joinPoint.getTarget()).thenReturn(new OrderServiceImpl());
		when(joinPoint.getArgs()).thenReturn(new Object[]{"A1", new OrderDto(42L)});
		return joinPoint;
	}
	
	private static CacheEvict annotationOf(Class<?> clazz, String methodName, Class<?>... types) throws Exception {
		Method m = clazz.getDeclaredMethod(methodName, types);
		return m.getAnnotation(CacheEvict.class);
	}
	
	@Test
	void annotationResolvesWhenJoinPointSignatureIsInterfaceMethod() throws Exception {
		// JDK 动态代理下 signature.getMethod() 返回接口方法(无注解),
		// 修复前 getCacheEvictAnnotation 返回 null, 调用点直接 NPE
		Method ifaceMethod = OrderService.class.getDeclaredMethod("update", String.class, OrderDto.class);
		MethodSignature signature = mock(MethodSignature.class);
		when(signature.getMethod()).thenReturn(ifaceMethod);
		JoinPoint joinPoint = mock(JoinPoint.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(joinPoint.getTarget()).thenReturn(new OrderServiceImpl());
		
		CacheEvict resolved = CopilotCacheEvictAspect.resolveCacheEvict(joinPoint);
		
		assertThat(resolved).isNotNull();
		assertThat(resolved.keys()).containsExactly("category_brands_#{#dto.brandId}", "plain_key");
	}
	
	@Test
	void missingAnnotationResolvesToNullAndBeforeSkipsQuietly() throws Exception {
		Method m = OrderServiceImpl.class.getDeclaredMethod("notAnnotated");
		MethodSignature signature = mock(MethodSignature.class);
		when(signature.getMethod()).thenReturn(m);
		JoinPoint joinPoint = mock(JoinPoint.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(joinPoint.getTarget()).thenReturn(new OrderServiceImpl());
		
		assertThat(CopilotCacheEvictAspect.resolveCacheEvict(joinPoint)).isNull();
		// 通知方法在无注解时安静跳过, 不影响业务主流程
		RecordingAspect aspect = new RecordingAspect();
		assertThatCode(() -> aspect.before(joinPoint)).doesNotThrowAnyException();
		assertThat(aspect.deleted).isEmpty();
	}
	
	@Test
	void variableStyleTemplateResolvesParameterProperties() throws Exception {
		//注解 javadoc 承诺的 "user:#user.id:info" 变量风格(#name 是 SpEL 变量引用):
		//旧实现用 DynamicUtils 造根对象只提供属性访问, 变量全部解析失败回退成模板原文
		Method m = OrderServiceImpl.class.getDeclaredMethod("update", String.class, OrderDto.class);
		
		List<String> keys = CopilotCacheEvictAspect.resolveEvictKeys(
			m, new String[]{"orderNo", "dto"}, new Object[]{"A1", new OrderDto(42L)},
			new String[]{"category_brands_#{#dto.brandId}", "plain_key"});
		
		assertThat(keys).containsExactly("category_brands_42", "plain_key");
	}
	
	@Test
	void propertyStyleTemplateStillResolves() throws Exception {
		//旧写法(不带#, 直接属性路径)保持可用
		Method m = OrderServiceImpl.class.getDeclaredMethod("update", String.class, OrderDto.class);
		
		List<String> keys = CopilotCacheEvictAspect.resolveEvictKeys(
			m, new String[]{"orderNo", "dto"}, new Object[]{"A1", new OrderDto(42L)},
			new String[]{"category_brands_#{dto.brandId}"});
		
		assertThat(keys).containsExactly("category_brands_42");
	}
	
	@Test
	void nullParameterNamesSkipsEvictionWithErrorLog() throws Exception {
		//业务模块没开 -parameters 编译时 Spring 6 拿不到参数名;
		//修复前返回未解析的模板原文去删一个不存在的 key, 日志却显示删除成功
		Method m = OrderServiceImpl.class.getDeclaredMethod("update", String.class, OrderDto.class);
		
		ListAppender<ILoggingEvent> appender = attachAppender();
		List<String> keys;
		try {
			keys = CopilotCacheEvictAspect.resolveEvictKeys(
				m, null, new Object[]{"A1", new OrderDto(42L)},
				new String[]{"category_brands_#{#dto.brandId}"});
		} finally {
			ASPECT_LOG.detachAppender(appender);
		}
		
		assertThat(keys).isEmpty();
		assertThat(assertThatMessages(appender)).anyMatch(s -> s.contains("-parameters"));
	}
	
	@Test
	void unresolvedTemplateKeyIsSkippedNotDeletedLiterally() throws Exception {
		//模板解析失败(#{} 求值抛异常)时, 该 key 不允许回退成模板原文去执行删除,
		//否则会删一个不存在的 key 并留下"已删除"的误导日志; 解析失败的 key 记 error 并跳过
		Method m = OrderServiceImpl.class.getDeclaredMethod("update", String.class, OrderDto.class);
		
		ListAppender<ILoggingEvent> appender = attachAppender();
		List<String> keys;
		try {
			keys = CopilotCacheEvictAspect.resolveEvictKeys(
				m, new String[]{"orderNo", "dto"}, new Object[]{"A1", new OrderDto(42L)},
				new String[]{"bad_#{#nonexistent.field.deep}"});
		} finally {
			ASPECT_LOG.detachAppender(appender);
		}
		
		assertThat(keys).isEmpty();
		assertThat(assertThatMessages(appender)).anyMatch(s -> s.contains("bad_"));
	}
	
	@Test
	void secondDeleteDelayUsesAnnotationOverrideThenGlobalDefault() throws Exception {
		CacheEvict withOverride = annotationOf(OrderServiceImpl.class, "zeroArg");
		assertThat(CopilotCacheEvictAspect.secondDeleteDelaySeconds(withOverride, 1L)).isEqualTo(3L);
		
		CacheEvict withoutOverride = annotationOf(OrderServiceImpl.class, "update", String.class, OrderDto.class);
		assertThat(CopilotCacheEvictAspect.secondDeleteDelaySeconds(withoutOverride, 2L)).isEqualTo(2L);
	}
	
	@Test
	void beforeDeletesResolvedKeysThroughEvictHook() throws Throwable {
		OrderServiceImpl target = new OrderServiceImpl();
		MethodSignature signature = mock(MethodSignature.class);
		when(signature.getMethod()).thenReturn(
			OrderServiceImpl.class.getDeclaredMethod("update", String.class, OrderDto.class));
		when(signature.getParameterNames()).thenReturn(new String[]{"orderNo", "dto"});
		JoinPoint joinPoint = mock(JoinPoint.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(joinPoint.getTarget()).thenReturn(target);
		when(joinPoint.getArgs()).thenReturn(new Object[]{"A1", new OrderDto(42L)});
		
		RecordingAspect aspect = new RecordingAspect();
		aspect.before(joinPoint);
		
		assertThat(aspect.deleted).containsExactly("category_brands_42", "plain_key");
	}
	
	@Test
	void schedulerRejectsNewDelayTaskWhenQueueFull() throws Exception {
		//延迟任务排队数达到上限时, 新的第二删被跳过并记 error 日志(可观测), 不允许无界排队
		RecordingAspect aspect = new RecordingAspect(1L, 1);
		Method m = OrderServiceImpl.class.getDeclaredMethod("zeroArg");
		CacheEvict ann = m.getAnnotation(CacheEvict.class);
		
		JoinPoint joinPoint = mock(JoinPoint.class);
		MethodSignature signature = mock(MethodSignature.class);
		when(signature.getMethod()).thenReturn(m);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(joinPoint.getTarget()).thenReturn(new OrderServiceImpl());
		when(joinPoint.getArgs()).thenReturn(new Object[]{});
		
		ListAppender<ILoggingEvent> appender = attachAppender();
		boolean first;
		boolean second;
		try {
			//第一次: 拿到排队名额, 进入调度队列(evictDelaySeconds=3, 测试期间不会真正执行)
			first = aspect.tryScheduleSecondDelete(joinPoint, ann);
			//第二次: 名额已满, 拒绝并记 error
			second = aspect.tryScheduleSecondDelete(joinPoint, ann);
		} finally {
			ASPECT_LOG.detachAppender(appender);
		}
		
		assertThat(first).isTrue();
		assertThat(second).isFalse();
		assertThat(assertThatMessages(appender)).anyMatch(s -> s.contains("排队任务已达上限"));
	}
	
	@Test
	void schedulingAfterShutdownDoesNotThrowAndDoesNotLeakSlot() throws Exception {
		//停机窗口(或手工调用 shutdown 后)再触发第二删: schedule 抛 RejectedExecutionException,
		//修复前 ①异常穿过 @AfterReturning 通知传给业务调用方(违反 @CacheEvict "删除失败不抛异常"契约)
		//②已占用的排队名额不归还(归还代码在从未入队的任务体 finally 里), 泄漏满后所有第二删误报"已达上限"
		RecordingAspect aspect = new RecordingAspect(1L, 1);
		CacheEvict ann = annotationOf(OrderServiceImpl.class, "zeroArg");
		JoinPoint joinPoint = mockUpdateJoinPoint();
		
		aspect.shutdown();
		
		assertThatCode(() -> aspect.tryScheduleSecondDelete(joinPoint, ann)).doesNotThrowAnyException();
		//名额没有被泄漏: 下一次调度(同样在 shutdown 后)仍走到"调度器已停止"分支而不是"排队已达上限"
		assertThat(aspect.tryScheduleSecondDelete(joinPoint, ann)).isFalse();
	}
	
	@Test
	void secondDeleteRunsAfterDelayWithKeysParsedOnMainThread() throws Throwable {
		//第二删核心语义: 延迟到期后真的执行删除, 删的是主线程提前解析好的 key
		RecordingAspect aspect = new RecordingAspect(1L, 10);
		JoinPoint joinPoint = mockUpdateJoinPoint();
		CacheEvict ann = CopilotCacheEvictAspect.resolveCacheEvict(joinPoint);
		
		assertThat(aspect.tryScheduleSecondDelete(joinPoint, ann)).isTrue();
		//调度立刻返回, 此刻(默认延迟1秒内)不应已执行
		assertThat(aspect.deleted).isEmpty();
		
		long deadline = System.currentTimeMillis() + 5_000;
		while (aspect.deleted.isEmpty() && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
		assertThat(aspect.deleted).containsExactly("category_brands_42", "plain_key");
		assertThat(aspect.stages).containsExactly("延迟第二删");
	}
	
	@Test
	void shutdownDrainsQueuedTasksWithinGracePeriod() throws Throwable {
		//停机收尾: 队列里已到期的任务应在 awaitTermination 宽限期内执行完, 而不是被丢弃
		RecordingAspect aspect = new RecordingAspect(1L, 10);
		JoinPoint joinPoint = mockUpdateJoinPoint();
		CacheEvict ann = CopilotCacheEvictAspect.resolveCacheEvict(joinPoint);
		aspect.tryScheduleSecondDelete(joinPoint, ann);
		
		//等到延迟(1秒)到期任务进入执行, 或最多等 2 秒后 shutdown 让其靠收尾窗口执行
		shutdownAndAssertDrained(aspect);
	}
	
	private void shutdownAndAssertDrained(RecordingAspect aspect) throws InterruptedException {
		aspect.shutdown();
		//shutdown 内部 awaitTermination(5s) 同步等待, 返回时队列任务应已执行完
		assertThat(aspect.deleted).containsExactly("category_brands_42", "plain_key");
	}
	
	@Test
	void interfaceArgStyleParamNamesAreReplacedByImplementationMethodNames() throws Throwable {
		// JDK 代理 + 接口(api 包)不带 -parameters 编译: signature.getMethod() 是接口方法,
		// getParameterNames() 给的是 arg0/arg1; 修复前模板 #dto.brandId 解析不出来被跳过,
		// 只删掉固定 key; 修复后应换算实现类方法拿到真实参数名, 模板 key 正常解析
		Method ifaceMethod = OrderService.class.getDeclaredMethod("update", String.class, OrderDto.class);
		MethodSignature signature = mock(MethodSignature.class);
		when(signature.getMethod()).thenReturn(ifaceMethod);
		when(signature.getParameterNames()).thenReturn(new String[]{"arg0", "arg1"});
		JoinPoint joinPoint = mock(JoinPoint.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(joinPoint.getTarget()).thenReturn(new OrderServiceImpl());
		when(joinPoint.getArgs()).thenReturn(new Object[]{"A1", new OrderDto(42L)});
		
		RecordingAspect aspect = new RecordingAspect();
		aspect.before(joinPoint);
		
		assertThat(aspect.deleted).containsExactly("category_brands_42", "plain_key");
	}
	
	private static ListAppender<ILoggingEvent> attachAppender() {
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		ASPECT_LOG.addAppender(appender);
		ASPECT_LOG.setLevel(Level.DEBUG);
		return appender;
	}
	
	private static List<String> assertThatMessages(ListAppender<ILoggingEvent> appender) {
		return appender.list.stream()
			.filter(e -> e.getLevel() == Level.ERROR)
			.map(ILoggingEvent::getFormattedMessage)
			.collect(Collectors.toList());
	}
}
