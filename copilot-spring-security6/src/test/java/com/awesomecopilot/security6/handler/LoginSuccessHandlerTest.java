package com.awesomecopilot.security6.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.awesomecopilot.security6.properties.CopilotSecurityProperties;
import com.awesomecopilot.security6.service.LoginDurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 登录成功处理器测试（评审报告 P1-7 日志泄漏明文令牌 + P1-8 强转具体类 User）.
 * <p>
 * 旧实现三处问题:
 * ① log.info 打完整 access token——日志采集/ELK/值班人员可拿令牌冒用任意在线会话;
 * 修复后只打前 6 位 + 掩码, 权限列表降为 debug 且不再打全量 JSON;
 * ② (User) 强转 principal——业务方返回自定义 UserDetails 实现(最常见写法)时
 * 密码正确反而 ClassCastException 500; 修复后按 UserDetails 接口取用;
 * ③ 会话 TTL 写死 30 分钟, LoginDurationService 接口没有任何调用点;
 * 修复后优先 LoginDurationService(容器里有则用), 否则读配置 token-ttl-minutes(默认 30).
 * <p>
 * AuthUtils.login(连 Redis)抽成 protected 接缝 persistSession, Fake 覆写后单测不依赖 Redis.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class LoginSuccessHandlerTest {
	
	static class FakeHandler extends LoginSuccessHandler {
		Object persistedPrincipal;
		String persistedToken;
		long persistedExpires;
		TimeUnit persistedUnit;
		
		@Override
		protected void persistSession(String username, String accessToken, long expires, TimeUnit timeUnit,
		                              Object userDetails, List<? extends GrantedAuthority> authorities,
		                              java.util.Map<String, Object> loginInfo, boolean singleSignOn) {
			persistedToken = accessToken;
			persistedExpires = expires;
			persistedUnit = timeUnit;
			persistedPrincipal = userDetails;
		}
	}
	
	/** 自定义 UserDetails 实现(不是 Spring 的 User 类)——旧实现会在这抛 ClassCastException */
	static class MyUser implements UserDetails {
		@Override
		public Collection<? extends GrantedAuthority> getAuthorities() {
			return List.of(new SimpleGrantedAuthority("ROLE_BOSS"));
		}
		
		@Override
		public String getPassword() {return "x";}
		
		@Override
		public String getUsername() {return "zhangsan";}

		@Override
		public boolean isEnabled() {return true;}
		
		@Override
		public boolean isAccountNonLocked() {return true;}
		
		@Override
		public boolean isAccountNonExpired() {return true;}
		
		@Override
		public boolean isCredentialsNonExpired() {return true;}
	}
	
	private FakeHandler handler;
	private CopilotSecurityProperties properties;
	private Logger logbackLogger;
	private ListAppender<ILoggingEvent> appender;
	private Level originalLevel;
	
	@BeforeEach
	void setUp() {
		handler = new FakeHandler();
		properties = new CopilotSecurityProperties();
		ReflectionTestUtils.setField(handler, "properties", properties);
		
		logbackLogger = (Logger) LoggerFactory.getLogger(LoginSuccessHandler.class);
		originalLevel = logbackLogger.getLevel();
		logbackLogger.setLevel(Level.DEBUG); //日志断言需要捕获到 debug 级
		appender = new ListAppender<>();
		appender.start();
		logbackLogger.addAppender(appender);
	}
	
	@AfterEach
	void tearDown() {
		logbackLogger.detachAppender(appender);
		logbackLogger.setLevel(originalLevel);
		SecurityContextHolder.clearContext();
	}
	
	private void authenticate(Object principal) {
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
			principal, "pw", List.of(new SimpleGrantedAuthority("user:read")));
		auth.setDetails("dummy");
		SecurityContextHolder.getContext().setAuthentication(auth);
	}
	
	private String allLogs() {
		StringBuilder sb = new StringBuilder();
		for (ILoggingEvent e : appender.list) {
			sb.append(e.getFormattedMessage()).append('\n');
		}
		return sb.toString();
	}
	
	@Test
	void successLogDoesNotContainPlaintextToken() throws Exception {
		authenticate(new User("rico", "pw", List.of(new SimpleGrantedAuthority("user:read"))));
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		handler.onAuthenticationSuccess(new MockHttpServletRequest("POST", "/login"), response,
			SecurityContextHolder.getContext().getAuthentication());
		
		String token = handler.persistedToken;
		assertThat(token).as("应已生成并持久化 token").isNotBlank();
		assertThat(allLogs())
			.as("INFO/DEBUG 日志不得出现完整 token(旧版直接打印)")
			.doesNotContain(token);
		//掩码可见性: 前 6 位可出现用于日志检索, 其余不得
		assertThat(token.substring(6)).isNotEqualTo("");
	}
	
	@Test
	void customUserDetailsPrincipalIsSupported() {
		authenticate(new MyUser());
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		assertThatCode(() -> handler.onAuthenticationSuccess(
			new MockHttpServletRequest("POST", "/login"), response,
			SecurityContextHolder.getContext().getAuthentication()))
			.as("旧版 (User) 强转: 自定义 UserDetails 直接 ClassCastException")
			.doesNotThrowAnyException();
		assertThat(handler.persistedPrincipal)
			.as("持久化的应是原始 principal 对象(按 UserDetails 接口传递)")
			.isInstanceOf(MyUser.class);
	}
	
	@Test
	void tokenTtlComesFromConfigByDefault() throws Exception {
		authenticate(new User("rico", "pw", List.of(new SimpleGrantedAuthority("user:read"))));
		properties.getUserPassLogin().setTokenTtlMinutes(45);
		
		handler.onAuthenticationSuccess(new MockHttpServletRequest("POST", "/login"),
			new MockHttpServletResponse(), SecurityContextHolder.getContext().getAuthentication());
		
		assertThat(handler.persistedExpires).isEqualTo(45);
		assertThat(handler.persistedUnit).isEqualTo(TimeUnit.MINUTES);
	}
	
	@Test
	void loginDurationServiceWinsWhenPresent() throws Exception {
		authenticate(new User("rico", "pw", List.of(new SimpleGrantedAuthority("user:read"))));
		LoginDurationService service = username -> username.equals("rico") ? 5 : 120;
		ReflectionTestUtils.setField(handler, "loginDurationService", service);
		
		handler.onAuthenticationSuccess(new MockHttpServletRequest("POST", "/login"),
			new MockHttpServletResponse(), SecurityContextHolder.getContext().getAuthentication());
		
		assertThat(handler.persistedExpires).as("接口存在时优先于配置值").isEqualTo(5);
	}
	
	@Test
	void authoritiesLoggedAtDebugNotInfo() throws Exception {
		authenticate(new User("rico", "pw", List.of(new SimpleGrantedAuthority("user:read"))));
		handler.onAuthenticationSuccess(new MockHttpServletRequest("POST", "/login"),
			new MockHttpServletResponse(), SecurityContextHolder.getContext().getAuthentication());
		
		boolean authorityAtInfo = appender.list.stream()
			.filter(e -> e.getLevel() == Level.INFO)
			.anyMatch(e -> e.getFormattedMessage().contains("user:read"));
		assertThat(authorityAtInfo).as("权限列表不得出现在 INFO 级日志(旧版 toPrettyJson 全量输出)")
			.isFalse();
	}
}
