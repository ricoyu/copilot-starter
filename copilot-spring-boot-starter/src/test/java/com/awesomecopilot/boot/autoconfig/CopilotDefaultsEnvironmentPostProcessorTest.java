package com.awesomecopilot.boot.autoconfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.io.support.SpringFactoriesLoader;
import java.util.List;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CopilotDefaultsEnvironmentPostProcessor 行为测试（评审报告 P1-4 修复的默认值补位）.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class CopilotDefaultsEnvironmentPostProcessorTest {
	
	private final CopilotDefaultsEnvironmentPostProcessor postProcessor = new CopilotDefaultsEnvironmentPostProcessor();
	
	@Test
	void injectsDefaultOnlyWhenPropertyAbsent() {
		MockEnvironment env = new MockEnvironment();
		postProcessor.postProcessEnvironment(env, null);
		
		assertThat(env.getProperty(CopilotDefaultsEnvironmentPostProcessor.ALLOW_CIRCULAR_REFERENCES))
			.isEqualTo("true");
	}
	
	@Test
	void userValueAlwaysWins() {
		//旧 jar 内 application.properties 的事故形态是"应用没写、jar 里那份却生效且不可预期";
		//现在用户显式写 false 必然覆盖补位值
		MockEnvironment env = new MockEnvironment();
		env.setProperty(CopilotDefaultsEnvironmentPostProcessor.ALLOW_CIRCULAR_REFERENCES, "false");
		postProcessor.postProcessEnvironment(env, null);
		
		assertThat(env.getProperty(CopilotDefaultsEnvironmentPostProcessor.ALLOW_CIRCULAR_REFERENCES))
			.isEqualTo("false");
	}
	
	/**
	 * 注册测试: spring.factories 的键必须是接口全限定名 org.springframework.boot.env.EnvironmentPostProcessor,
	 * 写错键(如漏 .env)Spring 不报错、只当没人注册——首轮独立评审实测抓到的正是这个, 用测试把注册本身看住.
	 */
	@Test
	void registeredUnderCorrectFactoryKey() {
		List<String> names = SpringFactoriesLoader.loadFactoryNames(
			EnvironmentPostProcessor.class, getClass().getClassLoader());
		assertThat(names).contains(CopilotDefaultsEnvironmentPostProcessor.class.getName());
	}
	
	@Test
	void defaultIsLowestPriority() {
		MockEnvironment env = new MockEnvironment();
		postProcessor.postProcessEnvironment(env, null);
		//补进来的源在末尾(优先级最低), 用户任何源里的值都排在它前面
		assertThat(env.getPropertySources().stream()
			.filter(ps -> CopilotDefaultsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME.equals(ps.getName()))
			.findFirst()).isPresent();
		java.util.List<String> names = new java.util.ArrayList<>();
		env.getPropertySources().forEach(ps -> names.add(ps.getName()));
		assertThat(names.get(names.size() - 1))
			.isEqualTo(CopilotDefaultsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
	}
}
