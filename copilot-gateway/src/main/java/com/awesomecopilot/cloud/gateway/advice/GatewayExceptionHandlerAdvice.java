package com.awesomecopilot.cloud.gateway.advice;

import com.awesomecopilot.cloud.gateway.exception.GatewayException;
import com.awesomecopilot.cloud.gateway.properties.CopilotGatewayExceptionProperties;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import static com.awesomecopilot.boot.enums.SystemError.GATEWAY_ERROR;
import static com.awesomecopilot.boot.enums.SystemError.GATEWAY_NOT_FOUND_SERVICE;
import static com.awesomecopilot.boot.enums.SystemError.SYSTEM_ERROR;

@Slf4j
public class GatewayExceptionHandlerAdvice {
	
	@Autowired
	private CopilotGatewayExceptionProperties copilotGatewayExceptionProperties;
	
	@ExceptionHandler(value = {ResponseStatusException.class})
	public Result handle(ResponseStatusException e) {
		log.error("response status exception:{}", e);
		return Results.status(GATEWAY_ERROR).build();
	}
	
	/**
	 * 404
	 *
	 * @param e
	 * @return
	 */
	@ExceptionHandler(value = {NotFoundException.class})
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Result handle(NotFoundException e) {
		log.error("not found exception:{}", e);
		return Results.status(GATEWAY_NOT_FOUND_SERVICE).build();
	}
	
	
	/**
	 * 无权访问 401 错误
	 *
	 * @param e
	 * @return
	 */
	@ExceptionHandler(value = {GatewayException.class})
	public Result handle(GatewayException e) {
		log.error("GatewayException:{}", e);
		return Results.status(e.getCode(), e.getMsg()).build();
	}
	
	
	@ExceptionHandler(value = {Throwable.class})
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public Result handle(Throwable e) {
		return Results.status(SYSTEM_ERROR).build();
	}
}
