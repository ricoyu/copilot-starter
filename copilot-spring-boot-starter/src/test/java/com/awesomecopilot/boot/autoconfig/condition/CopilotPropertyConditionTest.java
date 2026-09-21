package com.awesomecopilot.boot.autoconfig.condition;

import com.awesomecopilot.boot.autoconfig.CopilotAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OnCopilotPropertyCondition} 的行为测试（评审报告 P1-3）.
 * <p>
 * 背景(实测 spring-boot-autoconfigure 3.2.4 的 OnPropertyCondition 源码): 官方条件走
 * environment.getProperty(key) 字面查找, 驼峰/中划线互不可见; 而 @ConfigurationProperties
 * 两种形态都能绑定. "注解写 camelCase、用户按 Boot 惯例配 kebab-case"时条件与属性类各判各的,
 * 用户关不掉开关. 本条件用 Binder 绑定, 与属性绑定同一套规则.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class CopilotPropertyConditionTest {
	
	@ConditionalOnCopilotProperty("copilot.async-transaction")
	static class Flagged {
	}
	
	private static final OnCopilotPropertyCondition CONDITION = new OnCopilotPropertyCondition();
	
	private static ConditionContext contextWith(String... keyValues) {
		MockEnvironment env = new MockEnvironment();
		for (int i = 0; i < keyValues.length; i += 2) {
			env.setProperty(keyValues[i], keyValues[i + 1]);
		}
		return new ConditionContext() {
			@Override
			public Environment getEnvironment() {return env;}
			
			@Override
			public ResourceLoader getResourceLoader() {return new DefaultResourceLoader();}
			
			@Override
			public BeanDefinitionRegistry getRegistry() {return null;}
			
			@Override
			public ConfigurableListableBeanFactory getBeanFactory() {return null;}
			
			@Override
			public ClassLoader getClassLoader() {return getClass().getClassLoader();}
		};
	}
	
	private static boolean outcome(String... keyValues) {
		return CONDITION.matches(contextWith(keyValues), AnnotationMetadata.introspect(Flagged.class));
	}
	
	@Test
	void matchesWhenPropertyAbsentAndMatchIfMissingTrue() {
		assertThat(outcome()).isTrue();
	}
	
	@Test
	void kebabCaseFalseTurnsFlagOff() {
		assertThat(outcome("copilot.async-transaction", "false")).isFalse();
	}
	
	@Test
	void camelCaseFalseAlsoTurnsFlagOff() {
		//修复前(官方条件 + kebab 注解键): camelCase 配置读不到, matchIfMissing 生效, 关不掉
		assertThat(outcome("copilot.asyncTransaction", "false")).isFalse();
	}
	
	@Test
	void explicitTrueMatches() {
		assertThat(outcome("copilot.async-transaction", "true")).isTrue();
	}
	
	@Test
	void invalidValueThrowsInsteadOfQuietlyFallingBack() {
		//值无法解析为布尔时, 不允许悄悄按 matchIfMissing 装配(那会把用户的配置意图丢在暗处)
		assertThatThrownBy(() -> outcome("copilot.async-transaction", "maybe"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("copilot.async-transaction");
	}
	
	@Test
	void autoConfigurationStillLoadsByDefault() {
		//端到端: 不配任何属性时 CopilotAutoConfiguration 正常装配(默认值路径不回归).
		//手工起 context 并 setAllowCircularReferences(true)——模拟 SpringApplication 按
		//spring.main.allow-circular-references=true 配置容器(该默认值由本 starter 的
		//CopilotDefaultsEnvironmentPostProcessor 补位; ApplicationContextRunner 不走 SpringApplication
		//路径所以属性不生效). 上游 TransactionEvents 在 @PostConstruct 里自引用 getBean,
		//没有这个开关装配本身就会失败(评审修复过程中实测发现).
		org.springframework.context.annotation.AnnotationConfigApplicationContext ctx =
			new org.springframework.context.annotation.AnnotationConfigApplicationContext();
		ctx.setAllowCircularReferences(true);
		ctx.register(CopilotAutoConfiguration.class);
		try {
			ctx.refresh();
			assertThat(ctx.getBeanNamesForType(com.awesomecopilot.common.spring.context.ApplicationContextHolder.class)).hasSize(1);
			assertThat(ctx.getBeanNamesForType(com.awesomecopilot.common.spring.transaction.TransactionEvents.class)).hasSize(1);
		} finally {
			ctx.close();
		}
	}
}
