package com.awesomecopilot.security.processor;

import com.awesomecopilot.security.authority.WildcardGrantedAuthority;
import com.awesomecopilot.security.mixin.UsernamePasswordAuthenticationTokenMixin;
import com.awesomecopilot.security.mixin.WildcardGrantedAuthorityMixIn;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.awesomecopilot.security.mixin.GrantedAuthorityMixIn;
import com.awesomecopilot.security.mixin.SimpleGrantedAuthorityMixIn;
import com.awesomecopilot.security.mixin.UnmodifiableSetMixin;
import com.awesomecopilot.security.mixin.UserMixin;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collections;
import java.util.HashSet;

/**
 * 为Spring Security相关对象添加序列化/反序列化支持
 * 实现了BeanPostProcessor接口, 在每个Bean初始化后执行, 检查当前bean是不是ObjectMapper, 是的话给他添加
 * SpringSecurity相关的MixIn支持SpringSecurity负责对象的序列化/烦序列化
 * 因为SpringmvC环境会创建一个ObjectMapper的bean, 这边对其增强后, JacksonUtils那边会先检查Spring容器中有没有ObjectMapper,
 * 有的话取容器中的, 没有才自己new一个, 所以这边的增强也会作用与JacksonUtils
 * <p>
 * Copyright: Copyright (c) 2020-08-14 13:51
 * <p>
 * Company: Sexy Uncle Inc.
 * <p>
 
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class ObjectMapperBeanPostProcessor implements BeanPostProcessor, Ordered {
	
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}
	
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof ObjectMapper) {
			ObjectMapper objectMapper = (ObjectMapper)bean;
			objectMapper.addMixIn(GrantedAuthority.class, GrantedAuthorityMixIn.class);
			objectMapper.addMixIn(SimpleGrantedAuthority.class, SimpleGrantedAuthorityMixIn.class);
			objectMapper.addMixIn(WildcardGrantedAuthority.class, WildcardGrantedAuthorityMixIn.class);
			objectMapper.addMixIn(UsernamePasswordAuthenticationToken.class, UsernamePasswordAuthenticationTokenMixin.class);
			objectMapper.addMixIn(User.class, UserMixin.class);
			objectMapper.addMixIn(Collections.unmodifiableSet(new HashSet<>(0)).getClass(), UnmodifiableSetMixin.class);
			return objectMapper;
		}
		return bean;
	}
	
	@Override
	public int getOrder() {
		return Integer.MIN_VALUE;
	}
}
