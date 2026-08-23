package com.awesomecopilot.boot.autoconfig.processor;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 跟踪容器中的 ObjectMapper 是否已完成 Copilot 增强，避免重复 registerModule。
 */
public final class ObjectMapperDecorationTracker {

	private static final Set<ObjectMapper> DECORATED =
			Collections.newSetFromMap(new IdentityHashMap<>());

	private ObjectMapperDecorationTracker() {
	}

	public static boolean markDecorated(ObjectMapper objectMapper) {
		return DECORATED.add(objectMapper);
	}

	public static boolean isDecorated(ObjectMapper objectMapper) {
		return DECORATED.contains(objectMapper);
	}
}
