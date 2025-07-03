package com.awesomecopilot.security.constants;

import com.awesomecopilot.common.lang.errors.ErrorType;
import com.awesomecopilot.common.lang.errors.ErrorTypes;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.awesomecopilot.common.lang.errors.ErrorTypes.ACCOUNT_DISABLED;
import static com.awesomecopilot.common.lang.errors.ErrorTypes.ACCOUNT_EXPIRED;
import static com.awesomecopilot.common.lang.errors.ErrorTypes.ACCOUNT_LOCKED;
import static com.awesomecopilot.common.lang.errors.ErrorTypes.PASSWORD_EXPIRED;

/**
 * Spring Security常见异常与ErrorType之间的映射关系
 * <p>
 * Copyright: (C), 2021-03-30 17:00
 * <p>
 * <p>
 * Company: Information & Data Security Solutions Co., Ltd.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
public final class SpringSecurityExceptions {
	
	public static final ConcurrentMap<Class<? extends Exception>, ErrorType> ERRYR_TYPES = new ConcurrentHashMap<>();
	
	static {
		ERRYR_TYPES.put(BadCredentialsException.class, ErrorTypes.USERNAME_PASSWORD_MISMATCH);
		ERRYR_TYPES.put(LockedException.class, ACCOUNT_LOCKED);
		ERRYR_TYPES.put(CredentialsExpiredException.class, PASSWORD_EXPIRED);
		ERRYR_TYPES.put(DisabledException.class, ACCOUNT_DISABLED);
		ERRYR_TYPES.put(AccountExpiredException.class, ACCOUNT_EXPIRED);
	}
	
	/**
	 * 返回这个异常类对应的错误码对象
	 * @param exceptionClass
	 * @return ErrorType
	 */
	public static ErrorType errorType(Class exceptionClass) {
		return ERRYR_TYPES.get(exceptionClass);
	}
}
