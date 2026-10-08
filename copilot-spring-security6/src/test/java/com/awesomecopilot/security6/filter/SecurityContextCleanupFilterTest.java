package com.awesomecopilot.security6.filter;

import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.security6.constants.ThreadLocalSecurityConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 请求收尾清理过滤器测试（评审报告 P2-10）.
 * <p>
 * 背景: UserContextHolder.clear() 此前全仓零调用, 上下文清理依赖可选引入的 web-starter
 * ThreadLocalCleanupListener(只覆盖 Tomcat 请求线程). 业务线程池复用线程时,
 * 上一个用户的 USER_ID/LOGIN_INFO/ACCESS_TOKEN 残留并被带进下一个任务.
 * 修复: security6 自带本过滤器, 请求结束(finally)清一次, 不依赖外部组件.
 * <p>
 * 链中抛出的异常同样要清理后原样抛出.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class SecurityContextCleanupFilterTest {
	
	@Test
	void clearsUserContextAfterChainCompletes() throws Exception {
		SecurityContextCleanupFilter filter = new SecurityContextCleanupFilter();
		ThreadContext.put(ThreadLocalSecurityConstants.USER_ID, 42L);
		ThreadContext.put(ThreadLocalSecurityConstants.ACCESS_TOKEN, "tok");
		
		filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(),
			new MockFilterChain());
		
		assertThat((Object) ThreadContext.get(ThreadLocalSecurityConstants.USER_ID)).isNull();
		assertThat((Object) ThreadContext.get(ThreadLocalSecurityConstants.ACCESS_TOKEN)).isNull();
	}
	
	@Test
	void clearsEvenWhenChainThrows() throws Exception {
		SecurityContextCleanupFilter filter = new SecurityContextCleanupFilter();
		ThreadContext.put(ThreadLocalSecurityConstants.LOGIN_INFO, "stale");
		
		MockFilterChain throwing = new MockFilterChain() {
			@Override
			public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
				throw new IllegalStateException("chain failed");
			}
		};
		//异常原样抛出(不吞), 且抛出前已完成清理
		assertThatCode(() -> filter.doFilter(new MockHttpServletRequest("GET", "/x"),
			new MockHttpServletResponse(), throwing))
			.isInstanceOf(IllegalStateException.class);
		assertThat((Object) ThreadContext.get(ThreadLocalSecurityConstants.LOGIN_INFO)).isNull();
	}
}
