package com.awesomecopilot.boot.autoconfig.processor;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 跟踪容器中的 ObjectMapper 是否已完成 Copilot 增强，避免重复 registerModule。
 * <p>
 * 线程安全说明: 底层集合用 synchronizedMap(IdentityHashMap) 包装, markDecorated 的
 * "判重+登记"由 Set.add 一次调用完成(synchronizedMap 对 add 底层的 Map.put 加锁)——
 * 先 isDecorated 再 markDecorated 的两步写法在并行 bean 初始化下会让同一 mapper
 * 被两边都判为未装饰、装饰两遍. 集合按引用相等判重, 装饰动作本身
 * (ObjectMapperDecorator.decorate) 不在此锁内执行, 只有登记在锁内.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public final class ObjectMapperDecorationTracker {
	
	private static final Set<ObjectMapper> DECORATED =
			Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<>()));
	
	private ObjectMapperDecorationTracker() {
	}
	
	/**
	 * 原子判重并登记: 第一次调用返回 true(调用方负责装饰), 之后一律 false.
	 */
	public static boolean markDecorated(ObjectMapper objectMapper) {
		return DECORATED.add(objectMapper);
	}
	
	public static boolean isDecorated(ObjectMapper objectMapper) {
		return DECORATED.contains(objectMapper);
	}
	
	/**
	 * 清空登记. 给测试与上下文重启(devtools 重启后旧 mapper 不应被强引用滞留)留的出口.
	 * 生产代码目前无人调用它, devtools 场景如需自动清空需另行接入 ContextClosedEvent 监听.
	 */
	public static void reset() {
		DECORATED.clear();
	}
}
