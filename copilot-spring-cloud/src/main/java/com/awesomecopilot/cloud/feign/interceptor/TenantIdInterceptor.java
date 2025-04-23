package com.awesomecopilot.cloud.feign.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * 实现feign接口调用传递tenentId请求头
 * <p>
 * Copyright: (C), 2023-03-05 17:07
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
public class TenantIdInterceptor implements RequestInterceptor {

	public static final String TENANT_ID_HEADER = "Tenant-Id";

	private static final Logger log = LoggerFactory.getLogger(TenantIdInterceptor.class);
	
	/**
	 * 只有PUT, POST方法需要做幂等性
	 */
	//private static String[] INTERCEPT_METHODS = new String[]{"POST", "PUT"};
	
	@Override
	public void apply(RequestTemplate template) {
		ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		HttpServletRequest request = requestAttributes.getRequest();
		// 从请求头获取 Tenant-Id
		String tenantId = request.getHeader(TENANT_ID_HEADER);
		/*
		 * 客户端调用服务A, 服务A通过feign调用服务B
		 * 注意这里拿到的是服务A的Controller方法的Method, 不是feign调用的服务B的method, 所以不能只拦截POST, PUT方法, 因为有可能
		 * 客户端调服务A走的GET, 服务A调服务B走的POST(需要做接口幂等性), 这种情况如果加了调用方法限制, 这个幂等性控制就不生效了
		 * 所以干脆去掉, 反正多个请求头也没事
		 */
		/*String method = request.getMethod();
		boolean shouldIntercept = false;
		for (int i = 0; i < INTERCEPT_METHODS.length; i++) {
			if (method.equalsIgnoreCase(INTERCEPT_METHODS[i])) {
				shouldIntercept = true;
			}
		}*/
		
		//设置Idempotent请求头
		if (!template.headers().containsKey(TENANT_ID_HEADER)) {
			if (isNotBlank(tenantId)) {
				log.info("添加{}请求头: {}", TENANT_ID_HEADER, tenantId);
				template.header(TENANT_ID_HEADER, tenantId);
			}
		}
	}
}
