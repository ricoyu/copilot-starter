package com.awesomecopilot.boot.jackson;

import com.awesomecopilot.security.authority.WildcardGrantedAuthority;
import com.awesomecopilot.security.processor.ObjectMapperBeanPostProcessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.util.Arrays;

public class UserSerializeTest {

	@Test
	public void test() {
		ObjectMapperBeanPostProcessor processor = new ObjectMapperBeanPostProcessor();
		ObjectMapper objectMapper = new ObjectMapper();
		processor.postProcessAfterInitialization(objectMapper, "user");

		WildcardGrantedAuthority authority = new WildcardGrantedAuthority("ROLE_USER");
		WildcardGrantedAuthority authority2 = new WildcardGrantedAuthority("ROLE_ADMIN");

		User user = new User("ricoyu", "123456", Arrays.asList(authority, authority2));

		try {
			String userJson = objectMapper.writeValueAsString(user);
			User user1 = objectMapper.readValue(userJson, User.class);
			System.out.println(user1);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
