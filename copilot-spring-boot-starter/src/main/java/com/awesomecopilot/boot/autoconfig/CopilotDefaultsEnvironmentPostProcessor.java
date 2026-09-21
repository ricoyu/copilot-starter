package com.awesomecopilot.boot.autoconfig;

import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Collections;

/**
 * 为没有显式配置的应用补上 spring.main.allow-circular-references=true 的默认值
 * (评审报告 P1-4: 替换原先打包在 jar 根目录的 application.properties).
 * <p>
 * 为什么这个默认值确实需要: 上游 commons-spring 的 TransactionEvents 在 @PostConstruct 里
 * 执行 getBean(TransactionEvents.class) 自引用获取静态单例, 这种"初始化期取自己"只有允许
 * 提前暴露单例(singletonFactory 早引用)时才可行——Spring Boot 2.6+ 默认关闭该能力,
 * 补上默认值前 CopilotAutoConfiguration 装配 TransactionEvents 会直接启动失败
 * (BeanCurrentlyInCreationException, 由端到端测试实测发现).
 * <p>
 * 与旧打包文件方式的区别: 属性以最低优先级(addLast)注入——用户在 application.yml 里
 * 显式写 false(或任何值)都会覆盖它并生效, 不再是"依赖 jar 与应用的同名文件谁先被 classpath
 * 命中"的不确定行为; 应用也看不到一份"自己文件里没写却生效了"的隐形配置.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class CopilotDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
	
	static final String PROPERTY_SOURCE_NAME = "copilotDefaults";
	
	static final String ALLOW_CIRCULAR_REFERENCES = "spring.main.allow-circular-references";
	
	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment,
	                                   org.springframework.boot.SpringApplication application) {
		//用户已配置(任意值/任意源)则完全尊重, 不注入
		if (environment.containsProperty(ALLOW_CIRCULAR_REFERENCES)) {
			return;
		}
		environment.getPropertySources().addLast(
			new MapPropertySource(PROPERTY_SOURCE_NAME,
				Collections.singletonMap(ALLOW_CIRCULAR_REFERENCES, "true")));
	}
	
	@Override
	public int getOrder() {
		//在配置文件类属性源之后运行即可(它只检查"是否已被配置")
		return Ordered.LOWEST_PRECEDENCE;
	}
}
