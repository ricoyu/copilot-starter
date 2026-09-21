package com.awesomecopilot.boot.es.autoconfig;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CopilotESUsageGuardAutoConfiguration 防呆提示的行为测试（评审报告 P2-2 修订）.
 * <p>
 * 验证两件事: ①copilot.es.enabled 缺省时 ES 自动配置不装配(不触碰 transport 连接);
 * ②用户配了 copilot.es.* 使用项却没开 enabled 时, 启动日志给出点名提示, 不让人对着"配了没反应"排查无门.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class CopilotESUsageGuardAutoConfigurationTest {
	
	private static final Logger GUARD_LOG =
		(Logger) LoggerFactory.getLogger(CopilotESUsageGuardAutoConfiguration.class);
	
	private List<ILoggingEvent> runGuardAndCapture(MockEnvironment env) {
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		GUARD_LOG.addAppender(appender);
		Level previous = GUARD_LOG.getLevel();
		GUARD_LOG.setLevel(Level.DEBUG);
		try {
			CopilotESUsageGuardAutoConfiguration.warnIfUsageWithoutEnable(env);
		} finally {
			GUARD_LOG.detachAppender(appender);
			GUARD_LOG.setLevel(previous);
		}
		return appender.list;
	}
	
	@Test
	void usageIntentWithoutEnabledGetsWarn() {
		MockEnvironment env = new MockEnvironment();
		//yml 列表写法生成的是 copilot.es.templates[0] 这种带下标的键
		env.setProperty("copilot.es.templates[0]", "event_template.json");
		
		List<ILoggingEvent> warns = runGuardAndCapture(env).stream()
			.filter(e -> e.getLevel() == Level.WARN)
			.collect(Collectors.toList());
		
		assertThat(warns).hasSize(1);
		assertThat(warns.get(0).getFormattedMessage()).contains("copilot.es.enabled");
	}
	
	@Test
	void explicitFalseEnabledMeansIntentionalOptOutAndStaysSilent() {
		//多环境工程常见: dev 配全套 copilot.es.*, prod 用 enabled:false 主动关.
		//显式写了 enabled=false 是明确决定, 每次启动再 warn 就是固定噪声——只有键完全缺失且存在使用项才提示
		MockEnvironment env = new MockEnvironment();
		env.setProperty("copilot.es.enabled", "false");
		env.setProperty("copilot.es.templates", "event_template.json");
		
		assertThat(runGuardAndCapture(env)).isEmpty();
	}
	
	@Test
	void warnNamesTheDetectedUsageKey() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("copilot.es.init", "true");
		
		List<ILoggingEvent> warns = runGuardAndCapture(env).stream()
			.filter(e -> e.getLevel() == Level.WARN)
			.collect(Collectors.toList());
		
		//提示的核心价值是报出用户实际写了哪个键(建议4①): 消息必须同时含命中键名与补救键名
		assertThat(warns).hasSize(1);
		assertThat(warns.get(0).getFormattedMessage())
			.contains("copilot.es.init")
			.contains("copilot.es.enabled");
	}
	
	@Test
	void guardRunsAtContextStartupWiring() {
		//建议3: 证明容器启动时真的会调用 afterPropertiesSet——只调静态方法的两组测试删掉钩子调用依然全绿
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		GUARD_LOG.addAppender(appender);
		Level previous = GUARD_LOG.getLevel();
		GUARD_LOG.setLevel(Level.DEBUG);
		try {
			new ApplicationContextRunner()
				.withPropertyValues("copilot.es.templates[0]=event_template.json")
				.withConfiguration(AutoConfigurations.of(CopilotESUsageGuardAutoConfiguration.class))
				.run(context -> assertThat(context).hasSingleBean(CopilotESUsageGuardAutoConfiguration.class));
		} finally {
			GUARD_LOG.detachAppender(appender);
			GUARD_LOG.setLevel(previous);
		}
		
		assertThat(appender.list.stream()
			.filter(e -> e.getLevel() == Level.WARN)
			.map(ILoggingEvent::getFormattedMessage)
			.collect(Collectors.toList()))
			.anyMatch(s -> s.contains("copilot.es.templates[0]"));
	}
	
	@Test
	void autoConfigurationImportsResolveToRealClasses() throws Exception {
		//建议4②: imports 文件是幽灵注册防线, 测试用 AutoConfigurations.of() 直列类名会绕过它
		var resource = getClass().getClassLoader()
			.getResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
		assertThat(resource).isNotNull();
		List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of(resource.toURI())).stream()
			.map(String::trim).filter(s -> !s.isEmpty()).toList();
		assertThat(lines).contains(
			"com.awesomecopilot.boot.es.autoconfig.CopilotESUsageGuardAutoConfiguration",
			"com.awesomecopilot.boot.es.autoconfig.CopilotESAutoConfiguration");
		for (String line : lines) {
			Class.forName(line);   //注册了不存在的类会让应用启动阶段抛 ClassNotFound, 这里在测试期先暴露
		}
	}
	
	@Test
	void enabledTrueGetsNoWarn() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("copilot.es.enabled", "true");
		env.setProperty("copilot.es.templates", "event_template.json");
		
		assertThat(runGuardAndCapture(env)).isEmpty();
	}
	
	@Test
	void noUsageAtAllGetsNoWarn() {
		assertThat(runGuardAndCapture(new MockEnvironment())).isEmpty();
	}
	
	@Test
	void esAutoConfigurationIsNotLoadedByDefault() {
		//matchIfMissing=false 的核心行为: 什么都不配时 ES 自动配置 bean 不存在, 因此不会建 transport 连接
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
				CopilotESUsageGuardAutoConfiguration.class,
				CopilotESAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasSingleBean(CopilotESUsageGuardAutoConfiguration.class);
				assertThat(context).doesNotHaveBean(CopilotESAutoConfiguration.class);
			});
	}
}
