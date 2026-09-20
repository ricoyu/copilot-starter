package com.awesomecopilot.boot.es.autoconfig;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * ES 使用意图防呆提示(自 copilot.es.enabled 缺省值改为 false 起配套存在).
 * <p>
 * enabled 缺省 false 后, "引入了 starter、配了 copilot.es.templates/init 等使用项、
 * 却忘了写 copilot.es.enabled=true" 的用户会得到一个装配了 ES 配置类却不生效、
 * 且无任何提示的现场. 本配置类不受 enabled 条件约束、总是装载, 启动时检测这种组合并 warn 点名,
 * 把"配了没反应"变成"日志里直接告诉你差哪个键".
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
@AutoConfiguration
@Slf4j
public class CopilotESUsageGuardAutoConfiguration implements InitializingBean {
	
	@Autowired
	private Environment environment;
	
	@Override
	public void afterPropertiesSet() {
		warnIfUsageWithoutEnable(environment);
	}
	
	/**
	 * copilot.es.enabled 键在 Environment 中完全缺失、且存在任何 copilot.es.* 使用项时记 warn 点名.
	 * <p>
	 * 显式写了 enabled=false 视为主动关闭, 不提示: 多环境工程常在各 profile 配全套 copilot.es.* 再在
	 * 生产用 enabled=false 关掉, 对这种明确决定每次启动再打一条 warn 属于固定噪声.
	 * <p>
	 * 键枚举按 propertySource 原始键名字面匹配, 覆盖 properties/yml 写法(含 yml 列表产生的
	 * copilot.es.templates[0] 带下标键与驼峰写法); 以下两类使用项检测不到(只会漏提示, 不会误提示):
	 * 环境变量形式(COPILOT_ES_TEMPLATES 等下划线大写键名)、非 EnumerablePropertySource 的自定义属性源.
	 */
	static void warnIfUsageWithoutEnable(Environment environment) {
		if (environment == null) {
			return;
		}
		//enabled 键显式存在(任意值)=用户已对开关做出决定; 值能解析为 true 的更无需提示
		if (Boolean.parseBoolean(environment.getProperty("copilot.es.enabled", "false"))
			|| environment.containsProperty("copilot.es.enabled")) {
			return;
		}
		if (!(environment instanceof ConfigurableEnvironment configurable)) {
			return;
		}
		for (PropertySource<?> propertySource : configurable.getPropertySources()) {
			if (!(propertySource instanceof EnumerablePropertySource<?> enumerable)) {
				continue;
			}
			for (String name : enumerable.getPropertyNames()) {
				if (name.startsWith("copilot.es.") && !name.equals("copilot.es.enabled")) {
					log.warn("检测到 ES 配置键 {} —— 但 copilot.es.enabled 未设为 true, ES 自动配置(连接/索引模板)不会生效."
						+ " 需要启用请显式配置 copilot.es.enabled=true", name);
					return;
				}
			}
		}
	}
}
