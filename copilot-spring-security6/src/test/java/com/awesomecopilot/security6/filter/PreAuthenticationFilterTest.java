package com.awesomecopilot.security6.filter;

import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.cache.auth.AuthUtils;
import com.awesomecopilot.security6.properties.CopilotSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Token 预认证过滤器测试（评审报告 P2-11 写完响应仍放行 / P2-12 与登录过滤器两套路径解析）.
 * <p>
 * 旧实现: getPreAuthenticatedPrincipal 里 writeJson(TOKEN_MISSING) 后 return null,
 * 父类 doFilter 继续 chain.doFilter——下游 Security 组件可能对同一响应二次写出,
 * 客户端看到哪个错误码取决于提交顺序. 修复: 请求级检查挪进 doFilter,
 * 写完响应直接返回(链路终止), 不再进认证流程.
 * <p>
 * P2-12: 登录路径判定改为使用与 UsernamePasswordAuthenticationFilter 相同的
 * RequestMatcher 实例(AntPathRequestMatcher(loginUrl, POST)), 注入前保留旧回退逻辑.
 * <p>
 * token 查询用 mockStatic(AuthUtils) 拦截, 测试不连 Redis——生产代码里不留一行转发的查询方法.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class PreAuthenticationFilterTest {
	
	/** 只覆写白名单判断(单测里无 RequestContextHolder), 不连 Redis 的最小过滤器 */
	static class FakeFilter extends PreAuthenticationFilter {
		
		@Override
		protected boolean isInWhiteList(HttpServletRequest request) {
			return false;
		}
	}
	
	/** 装配父类 doFilter 认证流程需要的 AuthenticationManager(preauth provider + 桩 UserDetailsService, 不连 Redis) */
	static FakeFilter withAuthenticationManager() {
		FakeFilter f = new FakeFilter();
		ReflectionTestUtils.setField(f, "loginUrl", "/login");
		PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();
		provider.setPreAuthenticatedUserDetailsService(t ->
			new org.springframework.security.core.userdetails.User(t.getPrincipal().toString(), "",
				java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
		f.setAuthenticationManager(new org.springframework.security.authentication.ProviderManager(provider));
		return f;
	}
	
	private CopilotSecurityProperties properties;
	private MockedStatic<JedisUtils> jedisUtils;
	private MockedStatic<AuthUtils> authUtils;
	
	@BeforeEach
	void setUp() {
		properties = new CopilotSecurityProperties();
		// AuthUtils 的静态初始化块(第105行)在类加载时执行 JedisUtils.scriptLoad 真连 Redis 加载 lua 脚本,
		// 本机 Redis 未启动时 mockStatic(AuthUtils) 会在"初始化类"这一步直接失败(实测 JedisConnectionException)。
		// 所以先拦截 JedisUtils(它的类加载只建连接池不建连接, 安全), 让 scriptLoad 返回 null 通过类初始化,
		// 再拦截 AuthUtils 本体——测试全程不依赖 Redis。
		// 若 AuthUtils 拦截失败, 就地关闭已建立的 JedisUtils 拦截再抛错, 防止它残留影响同线程后续测试
		jedisUtils = mockStatic(JedisUtils.class);
		try {
			authUtils = mockStatic(AuthUtils.class);
		} catch (RuntimeException e) {
			jedisUtils.close();
			jedisUtils = null;
			throw e;
		}
	}
	
	@AfterEach
	void tearDown() {
		// 逐个关闭且互不阻断: 前一个 close 抛异常也要执行后一个, 否则拦截残留在同线程后续测试上
		try {
			if (authUtils != null) {
				authUtils.close();
			}
		} finally {
			if (jedisUtils != null) {
				jedisUtils.close();
			}
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}
	}
	
	private FakeFilter filter(String loginUrl) {
		FakeFilter f = new FakeFilter();
		f.setProperties(properties);
		ReflectionTestUtils.setField(f, "loginUrl", loginUrl);
		return f;
	}
	
	@Test
	void missingTokenStopsChainAndWritesSingleResponse() throws Exception {
		FakeFilter f = filter("/login");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
		request.setServletPath("/orders");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		
		f.doFilter(request, response, chain);
		
		assertThat(chain.getRequest()).as("写完 TOKEN_MISSING 必须终止链路(旧版继续放行)").isNull();
		assertThat(response.getContentAsString())
			.as("响应写了恰好一个 TOKEN_MISSING(4011), 不再叠加第二个错误体")
			.startsWith("{").endsWith("}");
		assertThat(response.getContentAsString().indexOf("4011"))
			.as("4011 只出现一次").isEqualTo(response.getContentAsString().lastIndexOf("4011"));
	}
	
	@Test
	void badFormatTokenStopsChain() throws Exception {
		FakeFilter f = filter("/login");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
		request.setServletPath("/orders");
		request.addHeader("Authorization", "Token abc"); //缺 Bearer 前缀
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		
		f.doFilter(request, response, chain);
		
		assertThat(chain.getRequest()).as("格式错误写完 TOKEN_INVALID(4012) 后终止").isNull();
		assertThat(response.getContentAsString()).contains("4012");
	}
	
	@Test
	void loginPathSkipsTokenCheckEntirely() throws Exception {
		FakeFilter f = filter("/login");
		//登录请求不需要 token: 不能因 Authorization 缺失被 4011 拦下(旧版靠 isLoginRequest 放行, 保留该语义)
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
		request.setServletPath("/login");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		
		f.doFilter(request, response, chain);
		
		assertThat(chain.getRequest()).as("登录请求必须放行").isSameAs(request);
		assertThat(response.getContentAsString()).as("登录请求不该被缺 token 拦下").doesNotContain("4011");
	}
	
	@Test
	void injectedRequestMatcherDecidesLoginPath() throws Exception {
		//P2-12: 注入 matcher 后, 路径判定完全由 matcher 决定——GET /login 在 POST-only matcher 下不是登录请求
		FakeFilter f = filter("/login");
		f.setLoginRequestMatcher(new AntPathRequestMatcher("/login", "POST"));
		MockHttpServletRequest get = new MockHttpServletRequest("GET", "/login");
		get.setServletPath("/login");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		
		f.doFilter(get, response, chain);
		
		//GET /login 不算登录请求 → 要求 token → 缺失即 4011 并终止
		assertThat(chain.getRequest()).isNull();
		assertThat(response.getContentAsString()).contains("4011");
	}
	
	@Test
	void validTokenAuthenticatesAndChainContinues() throws Exception {
		//独立评审建议7: 覆盖 principal 属性已设 -> super.doFilter -> 认证通过的完整路径
		authUtils.when(() -> AuthUtils.checkToken("abc123")).thenReturn("rico");
		Map<String, Object> loginInfo = new HashMap<>();
		loginInfo.put("userId", 1L);
		loginInfo.put("ip", "127.0.0.1");
		authUtils.when(() -> AuthUtils.loginInfo("abc123", Map.class)).thenReturn(loginInfo);
		
		FakeFilter f = withAuthenticationManager();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
		request.setServletPath("/orders");
		request.addHeader("Authorization", "Bearer abc123");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		
		f.doFilter(request, response, chain);
		
		assertThat(chain.getRequest()).as("认证通过后链路继续").isSameAs(request);
		assertThat(response.getContentAsString()).doesNotContain("4011").doesNotContain("4012");
		var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		assertThat(auth).as("SecurityContext 应被父类认证成功填充").isNotNull();
		assertThat(auth.getName()).isEqualTo("rico");
		assertThat(auth.isAuthenticated()).isTrue();
	}
}
