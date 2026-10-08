package com.awesomecopilot.security6.controller;

import com.awesomecopilot.security6.properties.CopilotSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证码生成端测试（评审报告 P1-2 的生成侧）.
 * <p>
 * 修复后写入 Redis 的值形态为 "{客户端IP}:{码文本}", 校验端据此拒绝"别人机器上生成的码"
 * (bind-ip=false 时校验端跳过比对, 值仍带前缀, 互不影响).
 * Redis 写入抽成 protected 接缝 storeCode, Fake 覆写后不连真实 Redis.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class VerifyCodeControllerTest {
	
	static class FakeController extends VerifyCodeController {
		Map<String, Object> stored;
		
		@Override
		protected void storeCode(String key, String valueWithIpPrefix, long ttlMinutes) {
			stored = Map.of("key", key, "value", valueWithIpPrefix, "ttl", ttlMinutes);
		}
		
		@Override
		protected String clientIp(jakarta.servlet.http.HttpServletRequest ignored) {
			return "10.1.2.3";
		}
	}
	
	@Test
	@SuppressWarnings("unchecked")
	void generatedCodeIsStoredWithIpPrefix() throws Exception {
		FakeController controller = new FakeController();
		ReflectionTestUtils.setField(controller, "properties", new CopilotSecurityProperties());
		
		var request = new MockHttpServletRequest("GET", "/pic-code");
		var result = controller.verificationCode(request);
		
		assertThat(result.getData()).isNotNull();
		Map<String, Object> data = (Map<String, Object>) result.getData();
		String codeId = (String) data.get("codeId");
		assertThat((String) controller.stored.get("key")).isEqualTo(("verifycode:" + codeId).toLowerCase());
		String value = (String) controller.stored.get("value");
		assertThat(value).as("存储值必须是 ip:code 形态")
			.startsWith("10.1.2.3:")
			.matches("10[.]1[.]2[.]3:[0-9A-Z]{4}");
	}
}
