package com.awesomecopilot.security6.advice;

import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.security6.exception.JwtTokenParseException;
import com.awesomecopilot.security6.exception.TokenExpiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.awesomecopilot.common.lang.errors.ErrorTypes.ACCESS_DENIED;
import static com.awesomecopilot.common.lang.errors.ErrorTypes.TOKEN_EXPIRED;
import static com.awesomecopilot.common.lang.errors.ErrorTypes.TOKEN_INVALID;


/**
 * <p>
 * Copyright: (C), 2021-04-01 13:57
 * <p>
 * <p>
 * Company: Information & Data Security Solutions Co., Ltd.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class RestSecurityExceptionAdvice {

	private static final Logger log = LoggerFactory.getLogger(RestSecurityExceptionAdvice.class);
	
	/**
	 * 解析JWT Token失败处理
	 *
	 * @param e
	 * @return
	 */
	@ExceptionHandler(JwtTokenParseException.class)
	@ResponseStatus(value = HttpStatus.OK)
	public ResponseEntity<Object> handleJwtTokenParseException(JwtTokenParseException e) {
		log.error("", e);
		Result result = Results.status(TOKEN_INVALID).build();
		return new ResponseEntity(result, HttpStatus.OK);
	}
	
	@ExceptionHandler(TokenExpiredException.class)
	@ResponseStatus(value = HttpStatus.OK)
	public ResponseEntity<Object> handleTokenExpiredException(TokenExpiredException e) {
		log.error("", e);
		Result result = Results.status(TOKEN_EXPIRED).build();
		return new ResponseEntity(result, HttpStatus.OK);
	}
	
	@ExceptionHandler(AccessDeniedException.class)
	@ResponseStatus(value = HttpStatus.OK)
	public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException e) {
		log.error("", e);
		Result result = Results.status(ACCESS_DENIED).build();
		return new ResponseEntity(result, HttpStatus.OK);
	}
}
