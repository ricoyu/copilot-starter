package com.awesomecopilot.cloud.gateway.handler;

import com.awesomecopilot.cloud.gateway.advice.GatewayExceptionHandlerAdvice;
import com.awesomecopilot.cloud.gateway.exception.GatewayException;
import com.awesomecopilot.common.lang.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import javax.annotation.Resources;
import java.util.Map;

/**
 * 网关错误处理, 返回JSON结果
 * <p>
 * Copyright: Copyright (c) 2020-05-02 10:50
 * <p>
 * Company: Sexy Uncle Inc.
 * <p>
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class CopilotErrorWebExceptionHandler extends DefaultErrorWebExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(CopilotErrorWebExceptionHandler.class);
	
	@Autowired
	private GatewayExceptionHandlerAdvice gateWayExceptionHandlerAdvice;
	
	/**
	 * Create a new {@code DefaultErrorWebExceptionHandler} instance.
	 *
	 * @param errorAttributes    the error attributes
	 * @param properties the resources configuration properties
	 * @param errorProperties    the error configuration properties
	 * @param applicationContext the current application context
	 */
	public CopilotErrorWebExceptionHandler(ErrorAttributes errorAttributes, ResourceProperties properties,
	                                       ErrorProperties errorProperties, ApplicationContext applicationContext) {
		super(errorAttributes, properties, errorProperties, applicationContext);
	}
	
	@Override
	protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
		return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
	}
	
	@Override
	protected Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
		Map<String, Object> error = getErrorAttributes(request, ErrorAttributeOptions.defaults());
		int errorStatus = (int) error.get("status");
		Throwable throwable = getError(request);
		Result result = null;
		if (throwable instanceof ResponseStatusException) {
			result = gateWayExceptionHandlerAdvice.handle((ResponseStatusException) throwable);
			//可以处理的异常都要返回HttpStatus.OK
			errorStatus = HttpStatus.OK.value();
		} else if (throwable instanceof NotFoundException) {
			result = gateWayExceptionHandlerAdvice.handle((NotFoundException) throwable);
			errorStatus = HttpStatus.OK.value();
		} else if (throwable instanceof GatewayException) {
			result = gateWayExceptionHandlerAdvice.handle((GatewayException) throwable);
			errorStatus = HttpStatus.OK.value();
		} else {
			result = gateWayExceptionHandlerAdvice.handle(throwable);
		}
		return ServerResponse.status(errorStatus)
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromObject(result));
	}
}
