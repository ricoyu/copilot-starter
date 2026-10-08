package com.awesomecopilot.security6.filter;

import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.common.lang.utils.StringUtils;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.security6.constants.SecurityConstants;
import com.awesomecopilot.security6.errors.SecurityErrors;
import com.awesomecopilot.security6.properties.CopilotSecurityProperties;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * 验证登录时提供的验证码是否正确
 * <p>
 * Copyright: (C), 2020-08-12 15:03
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
public class VerifyCodeFilter extends OncePerRequestFilter {
	
	private static final Logger log = LoggerFactory.getLogger(VerifyCodeFilter.class);
	
	@Autowired
	private CopilotSecurityProperties properties;
	
	/**
	 * 与 Spring Security 登录过滤器共用的 RequestMatcher(评审 P2-12):
	 * starter 装配时注入 AntPathRequestMatcher(loginUrl, POST), 路径与方法的判定规则集中一处.
	 * (独立评审更正过原表述: AntPathRequestMatcher 6.2.3 取路径同为 servletPath+pathInfo,
	 * 与回退分支并无两套解析差异; 本改动实际收益是规则集中 + 附带 POST 限定.)
	 * 为 null 时回退 servletPath 匹配(独立 new 出来的旧用法).
	 */
	private RequestMatcher loginRequestMatcher;
	
	public void setLoginRequestMatcher(RequestMatcher loginRequestMatcher) {
		this.loginRequestMatcher = loginRequestMatcher;
	}
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		if (!isLoginRequest(request)) {
			chain.doFilter(request, response);
			return;
		}
		
		//从request中拿codeId, verifyCode
		String codeId = request.getParameter(SecurityConstants.VERIFY_CODE_ID);
		String verifyCode = request.getParameter(SecurityConstants.VERIFY_CODE);
		log.info("doFilterInternal 开始, codeId={}", codeId);
		
		//没有提供codeId, verifyCode参数
		if (isBlank(codeId) || isBlank(verifyCode)) {
			log.info("doFilterInternal >> 验证码参数缺失, codeId={}, verifyCode为空={}", codeId, isBlank(verifyCode));
			Result result = Results.status(SecurityErrors.AUTH_CODE_MISS).build();
			RestUtils.writeJson(response, result);
			return;
		}
		
		String key = StringUtils.concat(SecurityConstants.VERIFY_CODE_PREFIX, codeId).toLowerCase();
		CopilotSecurityProperties.Feature.PicCode picCode = properties.getFeature().getPicCode();
		int maxWrong = picCode == null ? 5 : picCode.getMaxWrongAttempts();
		boolean bindIp = picCode == null || picCode.isBindIp();
		
		//1. 错误次数已达上限: 直接作废拒绝(评审 P1-2)
		if (wrongAttempts(key) >= maxWrong) {
			log.info("doFilterInternal >> 错误尝试已达上限, codeId 作废, codeId={}", codeId);
			invalidateCode(key);
			RestUtils.writeJson(response, Results.status(SecurityErrors.AUTH_CODE_EXPIRED).build());
			return;
		}
		
		//2. 读码(不删), 值形态 ip:code; 旧数据(纯 code, 无冒号)兼容到自然过期
		String stored = peekCode(key);
		if (stored == null) {
			log.info("doFilterInternal >> 验证码不存在或已被消费, codeId={}", codeId);
			RestUtils.writeJson(response, Results.status(SecurityErrors.AUTH_CODE_EXPIRED).build());
			return;
		}
		int sep = stored.lastIndexOf(':');
		String clientIp = clientIp(request);
		if (sep > 0 && bindIp && !stored.startsWith(clientIp + ":")) {
			long n = recordWrongAttempt(key);
			log.info("doFilterInternal >> 验证码非本客户端 IP 生成, codeId={}, 累计错误 {} 次", codeId, n);
			if (n >= maxWrong) {
				invalidateCode(key);
			}
			RestUtils.writeJson(response, Results.status(SecurityErrors.AUTH_CODE_MISMATCH).build());
			return;
		}
		String code = (sep > 0) ? stored.substring(sep + 1) : stored;
		
		if (!verifyCode.equalsIgnoreCase(code)) {
			long n = recordWrongAttempt(key);
			log.info("doFilterInternal >> 验证码不匹配, codeId={}, 累计错误 {} 次", codeId, n);
			SecurityErrors error = n >= maxWrong ? SecurityErrors.AUTH_CODE_EXPIRED : SecurityErrors.AUTH_CODE_MISMATCH;
			if (n >= maxWrong) {
				invalidateCode(key);
			}
			RestUtils.writeJson(response, Results.status(error).build());
			return;
		}
		
		//3. 比对成功: 原子取出即删(评审 P1-2 注释承诺的行为; 并发提交只有赢家能拿到值)
		if (consumeCode(key) == null) {
			log.info("doFilterInternal >> 验证码刚被并发请求消费, codeId={}", codeId);
			RestUtils.writeJson(response, Results.status(SecurityErrors.AUTH_CODE_EXPIRED).build());
			return;
		}
		log.info("doFilterInternal >> 验证码校验通过并已消费, codeId={}", codeId);
		chain.doFilter(request, response);
	}
	
	/**
	 * 是否登录请求: 优先用与登录过滤器相同的 matcher(P2-12), 未注入时回退 servletPath 匹配.
	 */
	protected boolean isLoginRequest(HttpServletRequest request) {
		if (loginRequestMatcher != null) {
			return loginRequestMatcher.matches(request);
		}
		return ServletUtils.pathMatch(request, properties.getUserPassLogin().getLoginUrl());
	}
	
	//==== Redis 接缝: protected 便于单测覆写, 也隔离底层 API 变化 ====
	/** 读码但不删(比对预检查用; 消费必须走 consumeCode) */
	protected String peekCode(String key) {
		return JedisUtils.get(key);
	}
	
	/** 原子取出即删除(Lua: get+del 一步完成); 返回 null 表示码不存在/已被消费 */
	protected String consumeCode(String key) {
		return JedisUtils.delGet(key);
	}
	
	/** 错误尝试计数+1(首次时挂 TTL), 返回累计次数 */
	protected long recordWrongAttempt(String key) {
		Long n = JedisUtils.incr(wrongKey(key), ttlMinutes(), MINUTES);
		return n == null ? -1 : n;
	}
	
	/** 只读当前错误次数, 不递增 */
	protected long wrongAttempts(String key) {
		String v = JedisUtils.get(wrongKey(key));
		return v == null ? 0 : Long.parseLong(v);
	}
	
	/** 直接作废该 codeId(码与错误计数 key 一并清: 码已删, 计数留着只是垃圾) */
	protected void invalidateCode(String key) {
		JedisUtils.del(key);
		JedisUtils.del(wrongKey(key));
	}
	
	/** 客户端真实 IP(经代理取 X-Forwarded-For 首段). protected 便于单测固定返回值 */
	protected String clientIp(HttpServletRequest request) {
		return ServletUtils.getRemoteRealIP(request);
	}
	
	private static String wrongKey(String codeKey) {
		return codeKey + ":wrong";
	}
	
	private long ttlMinutes() {
		CopilotSecurityProperties.Feature.PicCode picCode = properties.getFeature().getPicCode();
		return picCode == null ? 5 : Math.max(1, picCode.getTtl());
	}
}
