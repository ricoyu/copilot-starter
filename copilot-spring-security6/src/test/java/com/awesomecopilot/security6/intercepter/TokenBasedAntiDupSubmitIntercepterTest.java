package com.awesomecopilot.security6.intercepter;

import com.awesomecopilot.security6.annotation.AntiDupSubmit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 防重复提交拦截器测试（评审报告 P1-4）.
 * <p>
 * 旧实现两个问题:
 * ① Redis key 里直接拼完整 access token——能 SCAN/KEYS/MONITOR Redis 的运维或进程
 * 从 key 就能读出在线会话令牌, 拿去冒用登录态; 修复后 key 只存 token 的 SHA-256 十六进制;
 * ② 注释说 token 为空"跳过检查", 实际 return false 拦截且不写响应体(空 200),
 * 行为与注释相反; 修复后明确返回 401 + TOKEN_MISSING JSON.
 * <p>
 * Redis 写操作抽 protected 接缝 tryAcquire, Fake 覆写后不连真实 Redis.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class TokenBasedAntiDupSubmitIntercepterTest {
	
	static final String TOKEN = "e2f7…cdef";
	
	static class FakeIntercepter extends TokenBasedAntiDupSubmitIntercepter {
		final List<String> keys = new ArrayList<>();
		boolean acquireResult = true;
		
		@Override
		protected boolean tryAcquire(String key, long timeoutMillis) {
			keys.add(key);
			return acquireResult;
		}
	}
	
	//测试业务方法: 打注解与不打注解各一个
	@AntiDupSubmit(3000)
	public void annotated() {
	}
	
	public void plain() {
	}
	
	private HandlerMethod handlerOf(String methodName) throws NoSuchMethodException {
		Method m = getClass().getMethod(methodName);
		return new HandlerMethod(new Object(), m); //实例仅占位, 注解从 m 上读
	}
	
	private MockHttpServletRequest bearerRequest(String authorizationHeader) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/order/submit");
		if (authorizationHeader != null) {
			request.addHeader("Authorization", authorizationHeader);
		}
		return request;
	}
	
	@Test
	void redisKeyContainsHashedTokenNotTheTokenItself() throws Exception {
		FakeIntercepter interceptor = new FakeIntercepter();
		HandlerMethod hm = new HandlerMethod(this, getClass().getMethod("annotated"));
		
		boolean allowed = interceptor.preHandle(bearerRequest("Bearer " + TOKEN),
			new MockHttpServletResponse(), hm);
		assertThat(allowed).isTrue();
		assertThat(interceptor.keys).hasSize(1);
		String key = interceptor.keys.get(0);
		assertThat(key).as("key 不得含明文令牌").doesNotContain(TOKEN);
		assertThat(key).as("key 含方法全名 + token 的 SHA-256(64位hex)")
			.startsWith("anti:dup:submit:" + getClass().getName() + ".annotated:")
			.endsWith(sha256Hex(TOKEN));
	}
	
	@Test
	void blankTokenRejectedWithUnauthorizedJson() throws Exception {
		FakeIntercepter interceptor = new FakeIntercepter();
		HandlerMethod hm = new HandlerMethod(this, getClass().getMethod("annotated"));
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		boolean allowed = interceptor.preHandle(bearerRequest(null), response, hm);
		
		assertThat(allowed).as("无 token 不放行(行为不变)").isFalse();
		assertThat(response.getStatus()).as("修复前是空 200").isEqualTo(401);
		assertThat(response.getContentAsString()).contains("4011"); //TOKEN_MISSING
	}
	
	@Test
	void duplicateSubmissionStillRejectedWithJson() throws Exception {
		FakeIntercepter interceptor = new FakeIntercepter();
		interceptor.acquireResult = false;
		HandlerMethod hm = new HandlerMethod(this, getClass().getMethod("annotated"));
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		boolean allowed = interceptor.preHandle(bearerRequest("Bearer " + TOKEN), response, hm);
		
		assertThat(allowed).isFalse();
		assertThat(response.getContentAsString()).contains("4003"); //DUPLICATE_SUBMISSION
	}
	
	@Test
	void methodWithoutAnnotationPassesThrough() throws Exception {
		FakeIntercepter interceptor = new FakeIntercepter();
		HandlerMethod hm = new HandlerMethod(this, getClass().getMethod("plain"));
		
		assertThat(interceptor.preHandle(bearerRequest(null), new MockHttpServletResponse(), hm)).isTrue();
		assertThat(interceptor.keys).isEmpty();
	}
	
	private static String sha256Hex(String s) throws Exception {
		byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder();
		for (byte b : d) sb.append(String.format("%02x", b));
		return sb.toString();
	}
}
