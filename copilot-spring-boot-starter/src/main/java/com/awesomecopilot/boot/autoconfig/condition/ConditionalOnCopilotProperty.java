package com.awesomecopilot.boot.autoconfig.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按 copilot.* 配置开关决定是否装配——与 @ConditionalOnProperty 同语义, 但键的解析走
 * relaxed binding(与 @ConfigurationProperties 绑定同一套规则).
 * <p>
 * 存在的理由(实测 spring-boot-autoconfigure 3.2.4 OnPropertyCondition 源码): 官方条件用
 * environment.getProperty(key) 按注解键字面查找, 驼峰/中划线互不可见; 而 @ConfigurationProperties
 * 两种写法都能绑定. 于是"注解写 camelCase、用户按 Boot 惯例配 kebab-case"时, 条件认为属性缺失
 * 按 matchIfMissing 装配, 属性类却绑定成功为 false——两边判断不一致, 用户关不掉开关(评审报告 P1-3).
 * <p>
 * 值解析失败(如配了 "maybe")时启动直接失败并点名键, 不会悄悄按默认值装配.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnCopilotPropertyCondition.class)
public @interface ConditionalOnCopilotProperty {
	
	/**
	 * 属性键, 推荐写 kebab-case 规范形态(如 copilot.async-transaction);
	 * 用户侧 camelCase/kebab 两种配置都会被识别.
	 */
	String value();
	
	/**
	 * 期望值. 默认空: 属性值为 true 才装配(布尔语义直接).
	 */
	String havingValue() default "";
	
	/**
	 * 属性不存在时是否装配.
	 */
	boolean matchIfMissing() default true;
}
