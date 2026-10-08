package com.awesomecopilot.security6.intercepter;

import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.common.lang.errors.ErrorTypes;
import com.awesomecopilot.common.lang.utils.StringUtils;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.security6.annotation.AntiDupSubmit;
import com.awesomecopilot.web.utils.CORS;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.awesomecopilot.security6.constants.SecurityConstants.AUTHORIZATION_HEADER;
import static com.awesomecopilot.security6.constants.SecurityConstants.BEARER_TOKEN_PREFIX;
import static java.text.MessageFormat.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * 根据token以及所请求的方法，限定一定时间内不可重复提交
 * 
 * Copyright: Copyright (c) 2017-09-28 16:09
 * <p>
 * Company: DataSense
 * <p>
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 * @on
 */
public class TokenBasedAntiDupSubmitIntercepter implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(TokenBasedAntiDupSubmitIntercepter.class);
	private static final String TOKEN_ANTI_SUBMIT_KEY_TEMPLATE = "anti:dup:submit:{0}:{1}";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if (!(handler instanceof HandlerMethod)) {
			return true;
		}

		HandlerMethod handlerMethod = (HandlerMethod) handler;
		Method method = handlerMethod.getMethod();
		AntiDupSubmit antiDupSubmit = method.getAnnotation(AntiDupSubmit.class);
		
		if (antiDupSubmit != null) {
			long timeout = antiDupSubmit.value();
			String accessToken = getToken(request);
			if (isBlank(accessToken)) {
				//旧版注释写"跳过检查"实际 return false 拦截且响应体为空(空200), 行为与注释相反.
				//带 @AntiDupSubmit 的接口都要求登录态, 缺 token 明确回 401 + TOKEN_MISSING
				log.info("preHandle >> 请求缺少 Authorization token, 拒绝, uri={}", request.getRequestURI());
				reject(response, HttpStatus.UNAUTHORIZED, Results.status(ErrorTypes.TOKEN_MISSING).build());
				return false;
			}
			
			String fullMethodName = StringUtils.concat(method.getDeclaringClass().getName(), ".", method.getName());
			//评审 P1-4: key 不拼明文 token——能 SCAN/KEYS Redis 的人可直接读出在线会话令牌冒用登录态;
			//改存 token 的 SHA-256 十六进制(64字符), key 与该令牌一一对应但不可反推
			String key = format(TOKEN_ANTI_SUBMIT_KEY_TEMPLATE, fullMethodName, sha256Hex(accessToken));
			boolean success = tryAcquire(key, timeout);

			if (!success) {
				log.info("捕捉到重复提交了:{}", fullMethodName);
				reject(response, HttpStatus.OK, Results.status(ErrorTypes.DUPLICATE_SUBMISSION).build());
				return false;
			}
			log.info("preHandle >> 防重复提交检查通过, method={}, timeout={}ms", fullMethodName, timeout);
		}

		return true;
	}
	
	private static void reject(HttpServletResponse response, HttpStatus status, Result result) {
		//RestUtils.writeJson(三参) 内部已 setStatus + setContentType(application/json) + CORS(开关开时),
		//这里不再预设 content-type(独立评审建议5: 冗余)
		CORS.builder().allowAll().build(response);
		RestUtils.writeJson(response, status, result);
	}
	
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {

	}

	/** Redis 占位接缝(protected 便于单测覆写): setnx 占位成功即首次提交 */
	protected boolean tryAcquire(String key, long timeoutMillis) {
		return JedisUtils.setnx(key, "", timeoutMillis, MILLISECONDS);
	}
	
	private String getToken(HttpServletRequest request) {
		String accessToken = request.getHeader(AUTHORIZATION_HEADER);
		
		if (isBlank(accessToken)) {
			return null;
		}
		
		if (!accessToken.startsWith(BEARER_TOKEN_PREFIX)) {
			return null;
		}
		//去掉Bearer 前缀, 拿到真正的Token(旧版 replaceAll 把 "Bearer " 当正则处理, 语义相同但易误读)
		return accessToken.substring(BEARER_TOKEN_PREFIX.length());
	}
	
	private static String sha256Hex(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(64);
			for (byte b : digest) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			//JDK 规范要求 SHA-256 算法必须可用, 走到这里说明运行环境异常
			throw new IllegalStateException("SHA-256 不可用", e);
		}
	}

}
