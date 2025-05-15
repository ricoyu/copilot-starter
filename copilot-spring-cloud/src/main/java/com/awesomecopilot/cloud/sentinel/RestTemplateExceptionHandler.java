package com.awesomecopilot.cloud.sentinel;

import com.alibaba.cloud.sentinel.rest.SentinelClientHttpResponse;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.awesomecopilot.common.lang.errors.ErrorTypes;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.json.jackson.JacksonUtils;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

public class RestTemplateExceptionHandler {
	
	
	/**
	 * 限流后处理方法
	 *
	 * @param request
	 * @param body
	 * @param execution
	 * @param ex
	 * @return
	 */
	public static SentinelClientHttpResponse handleBlockException(HttpRequest request, byte[] body,
	                                                              ClientHttpRequestExecution execution,
	                                                              BlockException ex) {
		Result<Object> result = Results.status(ErrorTypes.FLOW_EXCEPTION).build();

		return new SentinelClientHttpResponse(JacksonUtils.toJson(result));
	}
	
	/**
	 * 熔断后处理的方法
	 *
	 * @param request
	 * @param body
	 * @param execution
	 * @param ex
	 * @return
	 */
	public static ClientHttpResponse handleFallback(HttpRequest request, byte[] body,
	                                                ClientHttpRequestExecution execution,
	                                                BlockException ex) {
		Result<Object> result = Results.status(ErrorTypes.DEGRADE_EXCEPTION).build();

		return new SentinelClientHttpResponse(JacksonUtils.toJson(result));
	}
}