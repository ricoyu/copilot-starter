package com.awesomecopilot.boot.autoconfig.condition;

import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * {@link ConditionalOnCopilotProperty} 的实现: 用 Binder 读取属性(与 @ConfigurationProperties
 * 同一套 relaxed binding 规则), 而非 Environment 字面查找.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class OnCopilotPropertyCondition implements Condition {
	
	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnCopilotProperty.class.getName());
		String key = (String) attributes.get("value");
		String havingValue = (String) attributes.get("havingValue");
		boolean matchIfMissing = (Boolean) attributes.get("matchIfMissing");
		
		Boolean present;
		try {
			//Binder 负责 relaxed binding: copilot.async-transaction 注解键能看见 asyncTransaction=true 的配置, 反之亦然
			present = Binder.get(context.getEnvironment())
				.bind(key, Boolean.class)
				.orElse(null);
		} catch (BindException e) {
			throw new IllegalStateException("@ConditionalOnCopilotProperty 属性 " + key
				+ " 的值无法解析为 true/false(非法值会让开关状态不可预期), 请改正配置", e);
		}
		
		if (present == null) {
			return matchIfMissing;
		}
		if (havingValue.isEmpty()) {
			return present;
		}
		return havingValue.equalsIgnoreCase(String.valueOf(present));
	}
}
