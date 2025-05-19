package com.awesomecopilot.gateway.sentinel.handler;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.DefaultBlockRequestHandler;
import com.alibaba.fastjson.JSON;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.awesomecopilot.common.lang.errors.ErrorTypes.GATEWAY_FLOW_EXCEPTION;

/**
 * 网关限流统一处理
 * <p>
 * Copyright: (C), 2020/4/25 13:55
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
public class GatewayBlockRequestHandler extends DefaultBlockRequestHandler {

	private static final Logger log = LoggerFactory.getLogger(GatewayBlockRequestHandler.class);

	@Override
	public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable ex) {
		if (acceptsHtml(exchange)) {
			return htmlErrorResponse(ex);
		}
		
		// JSON result
		return ServerResponse.status(HttpStatus.OK)
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromObject(buildErrorResult(ex)));
	}
	
	private boolean acceptsHtml(ServerWebExchange exchange) {
		try {
			List<MediaType> acceptedMediaTypes = exchange.getRequest().getHeaders().getAccept();
			acceptedMediaTypes.remove(MediaType.ALL);
			MediaType.sortBySpecificityAndQuality(acceptedMediaTypes);
			return acceptedMediaTypes.stream()
					.anyMatch(MediaType.TEXT_HTML::isCompatibleWith);
		} catch (InvalidMediaTypeException ex) {
			return false;
		}
	}
	
	private Mono<ServerResponse> htmlErrorResponse(Throwable ex) {
		return ServerResponse.status(HttpStatus.OK)
				.contentType(MediaType.TEXT_PLAIN)
				.body(BodyInserters.fromObject(JSON.toJSONString(buildErrorResult(ex))));
	}
	
	private Result buildErrorResult(Throwable e) {
		return Results.status(GATEWAY_FLOW_EXCEPTION).build();
	}
}
