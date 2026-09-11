package com.awesomecopilot.security6.deserializer;

import com.awesomecopilot.security6.authority.WildcardGrantedAuthority;
import com.awesomecopilot.security6.mixin.UserMixin;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.io.IOException;
import java.util.Set;

/**
 * Custom Deserializer for {@link User} class. This is already registered with {@link UserMixin}.
 * You can also use it directly with your mixin class.
 *
 * @author Jitendra Singh
 * @see UserMixin
 * @since 4.2
 */
public class UserDeserializer extends JsonDeserializer<User> {

	private static final Logger log = LoggerFactory.getLogger(UserDeserializer.class);

	/**
	 * This method will create {@link User} object. It will ensure successful object creation even if password key is null in
	 * serialized json, because credentials may be removed from the {@link User} by invoking {@link User#eraseCredentials()}.
	 * In that case there won't be any password key in serialized json.
	 *
	 * @param jp the JsonParser
	 * @param ctxt the DeserializationContext
	 * @return the user
	 * @throws IOException if a exception during IO occurs
	 * @throws JsonProcessingException if an error during JSON processing occurs
	 */
	@Override
	public User deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		ObjectMapper mapper = (ObjectMapper) jp.getCodec();
		JsonNode jsonNode = mapper.readTree(jp);
		//Set<GrantedAuthority> authorities = mapper.readValue(
		//		readJsonNode(jsonNode, "authorities").traverse(mapper), new TypeReference<Set<GrantedAuthority>>() {
		//		});
		Set<? extends GrantedAuthority> authorities = null;

		try {
			authorities = mapper.convertValue(jsonNode.get("authorities"), new TypeReference<Set<WildcardGrantedAuthority>>() {});
		}catch (Exception e){
			log.info("deserialize >> WildcardGrantedAuthority转换失败, 回退到SimpleGrantedAuthority");
			authorities = mapper.convertValue(jsonNode.get("authorities"), new TypeReference<Set<SimpleGrantedAuthority>>() {});
		}
		JsonNode password = readJsonNode(jsonNode, "password");
		String username = readJsonNode(jsonNode, "username").asText();
		User result =  new User(
				username, password.asText(""),
				readJsonNode(jsonNode, "enabled").asBoolean(), readJsonNode(jsonNode, "accountNonExpired").asBoolean(),
				readJsonNode(jsonNode, "credentialsNonExpired").asBoolean(),
				readJsonNode(jsonNode, "accountNonLocked").asBoolean(), authorities
		);

		if (password.asText(null) == null) {
			log.info("deserialize >> 密码为空, 清除凭证, username={}", username);
			result.eraseCredentials();
		}
		log.info("deserialize 结束, username={}, authorities={}", username, authorities != null ? authorities.size() : 0);
		return result;
	}

	private JsonNode readJsonNode(JsonNode jsonNode, String field) {
		return jsonNode.has(field) ? jsonNode.get(field) : MissingNode.getInstance();
	}
}
