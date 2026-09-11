package com.awesomecopilot.cloud.gateway.handler;

import com.awesomecopilot.cloud.gateway.advice.GatewayExceptionHandlerAdvice;
import com.awesomecopilot.cloud.gateway.exception.GatewayException;
import com.awesomecopilot.common.lang.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.WebProperties.Resources;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
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
public class CopilotErrorWebExceptionHandler extends DefaultErrorWebExceptionHandler implements Ordered {

	private static final Logger log = LoggerFactory.getLogger(CopilotErrorWebExceptionHandler.class);
	
	@Autowired
	private GatewayExceptionHandlerAdvice gatewayExceptionHandlerAdvice;
	
	/**
	 * Create a new {@code DefaultErrorWebExceptionHandler} instance.
	 *
	 * @param errorAttributes    the error attributes
	 * @param properties the resources configuration properties
	 * @param errorProperties    the error configuration properties
	 * @param applicationContext the current application context
	 */
	public CopilotErrorWebExceptionHandler(ErrorAttributes errorAttributes, Resources properties,
	                                       ErrorProperties errorProperties, ApplicationContext applicationContext) {
		super(errorAttributes, properties, errorProperties, applicationContext);
	}
	
	/**
	 * Return -2 so this handler runs before Spring Boot's default
	 * DefaultErrorWebExceptionHandler (order=-1), ensuring that gateway errors
	 * are always returned as JSON instead of HTML error pages
	 */
	@Override
	public int getOrder() {
		return -2;
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
			result = gatewayExceptionHandlerAdvice.handle((ResponseStatusException) throwable);
			//保留异常自带的状态码：静态资源/路径不存在等 4xx 不再伪装成 200，
			//否则浏览器会把 200 的 JSON 错误体当 JS 执行导致页面空白、排查困难
			errorStatus = ((ResponseStatusException) throwable).getStatusCode().value();
		} else if (throwable instanceof NotFoundException) {
			result = gatewayExceptionHandlerAdvice.handle((NotFoundException) throwable);
			errorStatus = HttpStatus.NOT_FOUND.value();
		} else if (throwable instanceof GatewayException) {
			result = gatewayExceptionHandlerAdvice.handle((GatewayException) throwable);
			//业务异常（如鉴权失败）保持 200 + 错误码，兼容既有前端
			errorStatus = HttpStatus.OK.value();
		} else {
			result = gatewayExceptionHandlerAdvice.handle(throwable);
		}
		return ServerResponse.status(errorStatus)
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromObject(result));
	}
}
