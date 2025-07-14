package com.awesomecopilot.boot.web.filter;

import com.awesomecopilot.boot.web.autoconfig.CopilotMvcProperties;
import com.awesomecopilot.codec.HashUtils;
import com.awesomecopilot.common.lang.errors.ErrorTypes;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.awesomecopilot.common.lang.errors.ErrorTypes.API_SIGN_FAILED;
import static com.awesomecopilot.common.lang.vo.Results.success;

public class ApiSignFilter extends OncePerRequestFilter {

	@Autowired
	private CopilotMvcProperties copilotMvcProperties;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		//没有开启接口签名则说明都不做
		if (!copilotMvcProperties.getApiSign().isEnabled()) {
			filterChain.doFilter(request, response);
			return;
		}

		//如果请求的是获取服务器时间戳
		if (ServletUtils.pathMatch(copilotMvcProperties.getApiSign().getTimestampPath())) {
			Result<Long> result = Results.<Long>success().data(System.currentTimeMillis()).build();
			RestUtils.writeJson(response, result);
			return;
		}

		/*
		 * 开始拿客户端设置的几个请求头值, 拼装字符串然后签名
		 */
		String headerStr = copilotMvcProperties.getApiSign().getHeaders().trim();
		//如果配置的时候最后多了一个逗号则去掉
		if (headerStr.endsWith(",")) {
			headerStr = headerStr.substring(0, headerStr.length() - 1);
		}
		String[] headers = headerStr.split(",");
		String uri = ServletUtils.requestPath();
		StringBuilder message = new StringBuilder();
		//按顺序拼接, uri=${uri}&timestamp=${timestamp}&nonce=${nonce}
		message.append("uri=").append(ServletUtils.requestPath());
		for (int i = 0; i < headers.length-1; i++) {
		    message.append("&").append(headers[i]).append("=");
			String header = ServletUtils.getHeader(headers[i]);
			message.append(header);
		}

		String SignatureHeader = headers[headers.length - 1];
		String providedSignature = ServletUtils.getHeader(SignatureHeader);
		String signature = HashUtils.sha256(message.toString());

		if (providedSignature.equals(signature)) {
			filterChain.doFilter(request, response);
		} else {
			Result<Object> result = Results.fail().status(API_SIGN_FAILED).build();
			RestUtils.writeJson(response, result);
			return;
		}
	}
}
