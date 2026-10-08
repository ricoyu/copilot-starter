package com.awesomecopilot.security6.filter;

import com.awesomecopilot.security6.properties.CopilotSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证码过滤器行为测试（评审报告 P1-2）.
 * <p>
 * 旧实现: get 读码后不删除, 一个 codeId 在 TTL(默认5分钟) 内可无限次复用; 错误尝试不计数;
 * 码不绑定生成者——等于一张固定通行证.
 * 修复后: 值形态 ip:code 绑定生成者 IP; 比对成功走 consumeCode(JedisUtils.delGet 原子取出即删,
 * 并发双击只有一个赢家); 错误尝试计数(incr+TTL), 达到上限作废该 codeId.
 * <p>
 * Redis 读写是 protected 接缝(peekCode/consumeCode/recordWrongAttempt/wrongAttempts/invalidateCode),
 * FakeFilter 覆写后单测不依赖真实 Redis.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class VerifyCodeFilterTest {
	
	//MockHttpServletRequest.getRemoteAddr() 默认 "127.0.0.1", 生成期绑定的就是它
	static final String KEY = "verifycode:abc";
	static final String IP = "127.0.0.1";
	
	static class FakeFilter extends VerifyCodeFilter {
		final Map<String, String> store = new HashMap<>();
		final Map<String, Long> wrongCounts = new HashMap<>();
		final List<String> consumed = new ArrayList<>();
		final List<String> invalidated = new ArrayList<>();
		
		@Override
		protected String peekCode(String key) {
			return store.get(key);
		}
		
		@Override
		protected String consumeCode(String key) {
			consumed.add(key);
			return store.remove(key);
		}
		
		@Override
		protected long recordWrongAttempt(String key) {
			return wrongCounts.merge(key, 1L, Long::sum);
		}
		
		@Override
		protected long wrongAttempts(String key) {
			return wrongCounts.getOrDefault(key, 0L);
		}
		
		@Override
		protected void invalidateCode(String key) {
			invalidated.add(key);
			store.remove(key);
		}
	}
	
	private FakeFilter filter;
	private CopilotSecurityProperties properties;
	
	@BeforeEach
	void setUp() {
		filter = new FakeFilter();
		properties = new CopilotSecurityProperties();
		ReflectionTestUtils.setField(filter, "properties", properties);
	}
	
	private MockHttpServletRequest loginRequest(String codeId, String code) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
		request.setServletPath("/login");
		if (codeId != null) request.setParameter("codeId", codeId);
		if (code != null) request.setParameter("code", code);
		return request;
	}
	
	private boolean chainInvoked(MockFilterChain chain) {
		return chain.getRequest() != null;
	}
	
	@Test
	void correctCodeIsConsumedAfterFirstUse() throws Exception {
		filter.store.put(KEY, IP + ":XK7P");
		
		MockFilterChain chain1 = new MockFilterChain();
		filter.doFilter(loginRequest("abc", "XK7P"), new MockHttpServletResponse(), chain1);
		assertThat(chainInvoked(chain1)).as("第一次用正确码应放行").isTrue();
		assertThat(filter.consumed).as("比对成功后必须走 consumeCode(取出即删)").containsExactly(KEY);
		
		MockHttpServletResponse response2 = new MockHttpServletResponse();
		MockFilterChain chain2 = new MockFilterChain();
		filter.doFilter(loginRequest("abc", "XK7P"), response2, chain2);
		assertThat(chainInvoked(chain2)).as("已消费的码不得再次放行").isFalse();
		assertThat(response2.getContentAsString()).contains("4010103"); //验证码不存在或已过期
	}
	
	@Test
	void codeFromAnotherIpRejectedAndCounted() throws Exception {
		properties.getFeature().getPicCode().setBindIp(true);
		filter.store.put(KEY, "10.0.0.9:" + "XK7P");  //别的机器生成的码
		
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(loginRequest("abc", "XK7P"), new MockHttpServletResponse(), chain);
		assertThat(chainInvoked(chain)).as("码不是本 IP 生成的不放行").isFalse();
		assertThat(filter.wrongCounts).as("IP 不匹配按一次错误尝试计数").containsValue(1L);
	}
	
	@Test
	void ipMismatchIgnoredWhenBindingDisabled() throws Exception {
		properties.getFeature().getPicCode().setBindIp(false);
		filter.store.put(KEY, "10.0.0.9:XK7P");
		
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(loginRequest("abc", "XK7P"), new MockHttpServletResponse(), chain);
		assertThat(chainInvoked(chain)).as("关闭 IP 绑定后只比码值").isTrue();
	}
	
	@Test
	void wrongCodeKeepsTicketUntilAttemptsLimit() throws Exception {
		properties.getFeature().getPicCode().setMaxWrongAttempts(3);
		filter.store.put(KEY, IP + ":XK7P");
		
		//第 1~2 次输错: 码还在(没被 consume 也没 invalidate), 第 3 次输对还能过
		filter.doFilter(loginRequest("abc", "WRONG"), new MockHttpServletResponse(), new MockFilterChain());
		filter.doFilter(loginRequest("abc", "WRONG"), new MockHttpServletResponse(), new MockFilterChain());
		assertThat(filter.invalidated).as("未到上限不作废").isEmpty();
		
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(loginRequest("abc", "XK7P"), new MockHttpServletResponse(), chain);
		assertThat(chainInvoked(chain)).as("上限内输对仍放行").isTrue();
	}
	
	@Test
	void attemptsAtLimitInvalidateCode() throws Exception {
		properties.getFeature().getPicCode().setMaxWrongAttempts(3);
		filter.store.put(KEY, IP + ":XK7P");
		
		for (int i = 0; i < 3; i++) {
			filter.doFilter(loginRequest("abc", "WRONG"), new MockHttpServletResponse(), new MockFilterChain());
		}
		assertThat(filter.wrongCounts).hasEntrySatisfying(KEY, n -> assertThat(n).isEqualTo(3));
		assertThat(filter.invalidated).as("错误达到上限该 codeId 作废").containsExactly(KEY);
		
		//作废后即使存储里重新出现同值, 预检查(计数已达上限)也必须直接拒绝
		filter.store.put(KEY, IP + ":XK7P");
		filter.invalidated.clear();
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(loginRequest("abc", "XK7P"), new MockHttpServletResponse(), chain);
		assertThat(chainInvoked(chain)).as("计数达上限后窗口内不再给机会").isFalse();
	}
	
	@Test
	void legacyValueWithoutIpStillMatchable() throws Exception {
		//升级窗口: 修复上线前生成、尚未过期消费的旧码(纯 code 无 ip 前缀)仍按码值比对放行,
		//避免部署瞬间所有在途验证码全失效
		filter.store.put(KEY, "XK7P");
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(loginRequest("abc", "XK7P"), new MockHttpServletResponse(), chain);
		assertThat(chainInvoked(chain)).isTrue();
	}
	
	@Test
	void missingParamsRejected() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(loginRequest(null, null), response, chain);
		assertThat(chainInvoked(chain)).isFalse();
		assertThat(response.getContentAsString()).contains("4010102"); //请提供验证码
	}
	
	@Test
	void nonLoginPathPassesThrough() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
		request.setServletPath("/products");
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(request, new MockHttpServletResponse(), chain);
		assertThat(chainInvoked(chain)).isTrue();
	}
	@Test
	void ipv6AddressBoundCodeMatchesItsOwnClient() throws Exception {
		//评审建议7: IPv6 地址本身含冒号, 拆分值形态时不能把 IP 截断.
		//码表不含冒号 => lastIndexOf(':') 取到的必是生成时写入的分隔符
		String v6 = "2001:db8::1";
		filter = new FakeFilter() {
			@Override
			protected String clientIp(HttpServletRequest request) {return v6;}
		};
		ReflectionTestUtils.setField(filter, "properties", properties);
		filter.store.put(KEY, v6 + ":XK7P");
		
		MockFilterChain ok = new MockFilterChain();
		filter.doFilter(loginRequest("abc", "XK7P"), new MockHttpServletResponse(), ok);
		assertThat(chainInvoked(ok)).as("IPv6 本机码应放行").isTrue();
		
		filter.store.put(KEY, v6 + ":ABCD");
		MockFilterChain wrongIp = new MockFilterChain();
		//码是 2001:db8::1 生成的, 当前客户端换成前缀相近的 2001:db8::11 不能命中绑定
		FakeFilter other = new FakeFilter() {
			@Override
			protected String clientIp(HttpServletRequest request) {return "2001:db8::11";}
		};
		ReflectionTestUtils.setField(other, "properties", properties);
		other.store.put(KEY, v6 + ":ABCD");
		other.doFilter(loginRequest("abc", "ABCD"), new MockHttpServletResponse(), wrongIp);
		assertThat(chainInvoked(wrongIp)).as("前缀相近的另一 IPv6 不放行").isFalse();
	}
	
	@Test
	void concurrentConsumptionOnlyOneWinner() throws Exception {
		//评审建议7: 两个请求都 peek 到同一码后各自尝试消费, Fake 的 consumeCode 是 store.remove,
		//第一次取到值、第二次取到 null => 落败方按"已被消费"拒绝
		filter.store.put(KEY, IP + ":XK7P");
		
		MockFilterChain first = new MockFilterChain();
		filter.doFilter(loginRequest("abc", "XK7P"), new MockHttpServletResponse(), first);
		assertThat(chainInvoked(first)).isTrue();
		
		MockHttpServletResponse loser = new MockHttpServletResponse();
		MockFilterChain second = new MockFilterChain();
		filter.doFilter(loginRequest("abc", "XK7P"), loser, second);
		assertThat(chainInvoked(second)).as("消费落败方不得放行(生产等价: delGet 返回 null)").isFalse();
		assertThat(loser.getContentAsString()).contains("4010103");
	}
}