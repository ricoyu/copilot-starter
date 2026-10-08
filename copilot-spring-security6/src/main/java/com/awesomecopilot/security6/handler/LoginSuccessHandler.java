package com.awesomecopilot.security6.handler;

import com.awesomecopilot.cache.auth.AuthUtils;
import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.common.lang.utils.StringUtils;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.security6.constants.ThreadLocalSecurityConstants;
import com.awesomecopilot.security6.properties.CopilotSecurityProperties;
import com.awesomecopilot.security6.service.LoginDurationService;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.awesomecopilot.security6.constants.ThreadLocalSecurityConstants.LOGIN_INFO;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * 登录成功后负责生成token
 * <p>
 * Copyright: Copyright (c) 2021-03-30 16:49
 * <p>
 * Company: Information & Data Security Solutions Co., Ltd.
 * <p>
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

	private static final Logger log = getLogger(LoginSuccessHandler.class);
	
	@Autowired
	private CopilotSecurityProperties properties;
	
	/**
	 * 应用如需"不同用户不同会话时长"就注册这个接口的 bean; 不注册则用
	 * copilot.security6.user-pass-login.token-ttl-minutes(默认 30 分钟).
	 */
	@Autowired(required = false)
	private LoginDurationService loginDurationService;
	
	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
	                                    Authentication authentication) throws IOException, ServletException {
		String accessToken = StringUtils.uniqueKey(66); //这是token
		String username = authentication.getName();
		//评审 P1-7: 旧版 INFO 直接打完整 token + 全量权限 JSON, 日志可见者能冒用任意在线会话.
		//现在日志只出现 token 前 6 位(检索够用), 权限摘要降到 DEBUG
		log.info("用户: {} 登录成功, Token前缀: {}", username, mask(accessToken));
		log.debug("用户: {} 的权限摘要: {}", username, authorityNames(authentication));
		
		//评审 P1-8: 旧版 (User) 强转, 业务方最常见的"自定义 UserDetails 实现"会在密码正确的前提下
		//当场 ClassCastException -> 500. 按 UserDetails 接口取用, 原始对象原样持久化
		Object principal = authentication.getPrincipal();
		if (!(principal instanceof UserDetails)) {
			log.error("登录成功但 principal 不是 UserDetails 实现, 拒绝建立会话, username={}, principalType={}",
				username, principal == null ? "null" : principal.getClass().getName());
			RestUtils.writeJson(response, Results.status(
				com.awesomecopilot.common.lang.errors.ErrorTypes.INTERNAL_SERVER_ERROR).build());
			return;
		}
		UserDetails userDetails = (UserDetails) principal;
		
		String ip = ServletUtils.getRemoteRealIP(request);
		doLogin(response, accessToken, username, userDetails, authentication, ip, false);
	}
	
	private void doLogin(HttpServletResponse response,
	                     String accessToken,
	                     String username,
	                     UserDetails userDetails,
	                     Authentication authentication,
	                     String ip,
	                     boolean singleSignOn) {
		log.info("doLogin 开始, username={}, ip={}, singleSignOn={}", username, ip, singleSignOn);
		Map<String, Object> loginInfo = ThreadContext.get(LOGIN_INFO);
		if (loginInfo == null) {
			loginInfo = new HashMap<>();
		}
		loginInfo.put("ip", ip);
		Object userId = ThreadContext.get(ThreadLocalSecurityConstants.USER_ID);
		loginInfo.put("userId", userId);

		long expires = resolveTtlMinutes(username);
		List<? extends GrantedAuthority> authorities =
			authentication.getAuthorities().stream().toList();
		persistSession(username, accessToken, expires, TimeUnit.MINUTES,
			userDetails, authorities, loginInfo, singleSignOn);
		Result result = Results.success().data(accessToken).build();
		log.info("doLogin 结束, username={}, expires={}分钟", username, expires);
		RestUtils.writeJson(response, result);
	}
	
	/** TTL 决策: 应用注册了 LoginDurationService 就问它(评审 P1-7: 旧版接口零调用), 否则用配置值 */
	private long resolveTtlMinutes(String username) {
		if (loginDurationService != null) {
			long minutes = loginDurationService.expiresInMinutes(username);
			if (minutes > 0) {
				return minutes;
			}
			//服务返回 0/负数会把会话立即判死, 按实现错误处理: 记 error 回退配置值(独立评审建议4)
			log.error("LoginDurationService 返回非法时长 {} 分钟(username={}), 回退配置值", minutes, username);
		}
		//properties 由容器注入; 业务方绕过容器直接 new 本处理器时兜底默认 30 分钟(独立评审建议4)
		return properties == null ? 30L : properties.getUserPassLogin().getTokenTtlMinutes();
	}
	
	/**
	 * 会话写入 Redis 接缝(AuthUtils.login 连 Redis, protected 便于单测覆写).
	 * 注意透传的是原始 UserDetails 对象序列化结果, 不做 (User) 向下转型.
	 */
	@SuppressWarnings("unchecked")
	protected void persistSession(String username, String accessToken, long expires, TimeUnit timeUnit,
	                              Object userDetails, List<? extends GrantedAuthority> authorities,
	                              Map<String, Object> loginInfo, boolean singleSignOn) {
		AuthUtils.login(username, accessToken, expires, timeUnit,
			userDetails, (List<?>) authorities, loginInfo, singleSignOn);
	}
	
	/** 日志用: token 只保留前 6 位 */
	private static String mask(String token) {
		return token.length() <= 6 ? "******" : token.substring(0, 6) + "****";
	}
	
	/** 日志用: 权限只列名字, 不打完整 JSON(对象图里可能带用户敏感字段) */
	private static String authorityNames(Authentication authentication) {
		return authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority).sorted().toList().toString();
	}
	
}
