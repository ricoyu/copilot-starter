package com.awesomecopilot.boot.autoconfig.processor;

import com.awesomecopilot.boot.autoconfig.properties.CopilotJacksonProperties;
import com.awesomecopilot.boot.autoconfig.properties.JacksonDeserializer;
import com.awesomecopilot.boot.autoconfig.properties.JacksonSerializer;
import com.awesomecopilot.common.lang.utils.CollectionUtils;
import com.awesomecopilot.common.lang.utils.ReflectionUtils;
import com.awesomecopilot.json.ObjectMapperDecorator;
import com.awesomecopilot.json.jackson.JacksonUtils;
import com.awesomecopilot.json.jackson.escapes.CustomCharacterEscapes;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * 在Spring容器启动的最后, 所有bean都ready后初始化一下JacksonUtils
 * <p>
 * 职责(web-starter 的 CopilotJacksonObjectMapperPostProcessor 没有覆盖的部分):
 * 1. 把容器 ObjectMapper 交给静态 JacksonUtils(反射写回其 objectMapper 字段);
 * 2. 应用 copilot.jackson.field-name-quote、copilot.jackson.serializers/deserializers 配置.
 * Copilot 装饰(ObjectMapperDecorator.decorate)与 web 侧共用 ObjectMapperDecorationTracker 判重,
 * 两个 starter 同时在 classpath 也只装饰一遍.
 * <p>
 * Copyright: (C), 2020/4/30 11:05
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Slf4j
public class ObjectMapperBeanPostProcessor implements SmartInitializingSingleton {
	
	private final ObjectProvider<ObjectMapper> objectMapperProvider;
	private final CopilotJacksonProperties copilotJacksonProperties;
	
	public ObjectMapperBeanPostProcessor(ObjectProvider<ObjectMapper> objectMapperProvider,
	                                     CopilotJacksonProperties copilotJacksonProperties) {
		this.objectMapperProvider = objectMapperProvider;
		this.copilotJacksonProperties = copilotJacksonProperties;
	}
	
	@Override
	public void afterSingletonsInstantiated() {
		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable();
		if (objectMapper == null) {
			//容器里没有 ObjectMapper bean(应用 exclude 了 Jackson 自动配置之类), JacksonUtils 用自身默认实例
			log.info("容器中没有 ObjectMapper bean, 跳过 JacksonUtils 接线与 copilot.jackson.* 配置");
			return;
		}
		log.info("初始化JacksonUtils......");
		if (JacksonUtils.objectMapper() != objectMapper) {
			if (ObjectMapperDecorationTracker.markDecorated(objectMapper)) {
				new ObjectMapperDecorator().decorate(objectMapper);
			}
			ReflectionUtils.setField("objectMapper", JacksonUtils.class, objectMapper);
		}
		
		// 配置输出JSON字段名不用双引号括起来
		if (!copilotJacksonProperties.isFieldNameQuote()) {
			objectMapper.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, false);
			objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
			
			//系列化字符串时候, Jackson会把双引号转义, 如\", 这里配置不需要转义
			//注意: 转义设置只存在于 JsonFactory 层(ObjectMapper 无全局 setCharacterEscapes),
			//若宿主用同一个 JsonFactory 构造了多个 mapper, 此设置会作用于全部同源 mapper
			JsonFactory factory = objectMapper.getFactory();
			if (factory.getCharacterEscapes() == null) {
				factory.setCharacterEscapes(new CustomCharacterEscapes());
			}
		}
		
		/*
		 * 配置自定义反序列化器/序列化器: 先收集进同一个 SimpleModule, 循环外一次性注册
		 * (旧代码在循环体内反复 registerModule 同一个 module, 靠 Jackson 的 typeId 内容哈希
		 * 才勉强得到覆盖效果, 语义脆弱且 O(n^2)); 实例化失败直接抛异常让启动终止——
		 * 配置写错必须当场暴露, 不允许"线上偶发反序列化行为不符且无人知配置未生效"
		 */
		SimpleModule customModule = new SimpleModule("copilot-jackson-custom");
		boolean hasCustom = false;
		if (CollectionUtils.isNotEmpty(copilotJacksonProperties.getDeserializers())) {
			for (JacksonDeserializer deserializer : copilotJacksonProperties.getDeserializers()) {
				if (deserializer.getType() == null || deserializer.getDeserializer() == null) {
					log.error("copilot.jackson.deserializers 条目不完整(type 或 deserializer 缺失): {}", deserializer);
					continue;
				}
				customModule.addDeserializer(deserializer.getType(),
					instantiate(deserializer.getDeserializer(), JsonDeserializer.class));
				hasCustom = true;
			}
		}
		if (CollectionUtils.isNotEmpty(copilotJacksonProperties.getSerializers())) {
			for (JacksonSerializer serializer : copilotJacksonProperties.getSerializers()) {
				if (serializer.getType() == null || serializer.getSerializer() == null) {
					log.error("copilot.jackson.serializers 条目不完整(type 或 serializer 缺失): {}", serializer);
					continue;
				}
				customModule.addSerializer(serializer.getType(),
					instantiate(serializer.getSerializer(), JsonSerializer.class));
				hasCustom = true;
			}
		}
		if (hasCustom) {
			objectMapper.registerModule(customModule);
		}
	}
	
	@SuppressWarnings("unchecked")
	private static <T> T instantiate(Class<?> clazz, Class<T> expect) {
		try {
			Object instance = clazz.getDeclaredConstructor().newInstance();
			if (!expect.isInstance(instance)) {
				throw new IllegalStateException(clazz.getName() + " 不是 " + expect.getSimpleName() + " 的子类");
			}
			return (T) instance;
		} catch (ReflectiveOperationException e) {
			//启动期显式失败: 配置引用了不存在/无公共无参构造的类, 悄悄跳过只会把问题拖到线上
			throw new IllegalStateException("实例化 " + clazz.getName() + " 失败, 需要 public 无参构造函数", e);
		}
	}
}
