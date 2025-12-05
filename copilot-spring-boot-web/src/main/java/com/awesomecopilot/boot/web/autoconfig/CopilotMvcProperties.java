package com.awesomecopilot.boot.web.autoconfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * Copyright: (C), 2022-01-26 13:56
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@ConfigurationProperties(prefix = "copilot.mvc")
public class CopilotMvcProperties {
	
	/**
	 * 保障接口幂等性的token有效期, 单位秒, 默认1小时
	 */
	private Integer idemtotentTokenTtl = 60 * 60;

	private boolean restExceptionAdviceEnabled = true;

	/**
	 * SpringMVC CORS 配置
	 */
	private CORS cors = new CORS();

	private APISign apiSign = new APISign(); //要初始化一下, 不然application.yaml没有配置这项的话, 拿到的apiSign就是null, 容易出空指针异常

	public Integer getIdemtotentTokenTtl() {
		return idemtotentTokenTtl;
	}

	public void setIdemtotentTokenTtl(Integer idemtotentTokenTtl) {
		this.idemtotentTokenTtl = idemtotentTokenTtl;
	}

	public boolean isRestExceptionAdviceEnabled() {
		return restExceptionAdviceEnabled;
	}

	public void setRestExceptionAdviceEnabled(boolean restExceptionAdviceEnabled) {
		this.restExceptionAdviceEnabled = restExceptionAdviceEnabled;
	}

	public APISign getApiSign() {
		return apiSign;
	}

	public void setApiSign(APISign apiSign) {
		this.apiSign = apiSign;
	}

	public CORS getCors() {
		return cors;
	}

	public void setCors(CORS cors) {
		this.cors = cors;
	}

	public static class APISign {

		/**
		 * 是否开启接口签名
		 */
		private boolean enabled;

		/**
		 * <ul>接口验签需要传递的请求头
		 *     <li/>timestamp 当前时间戳, 可以直接在获取客户端的当前时间戳, 如果担心客户端与服务端时间不一致，可以调用/timestamp接口获取服务器端当前时间戳
		 *     <li/>nonce 一个随机串, 客户端自行生成即可
		 *     <li/>signature 客户端生成的签名
		 *     <li/>客户端先拼装字符串: message = `uri=${uri}&timestamp=${timestamp}&nonce=${nonce}`;
		 *     <li/>对字符串用哈希算法生成签名信息: sha256(message) = signature
		 * </ul>
		 * 如果要自定义请求头, signature请放最后, 其余请求头按照这配配置的顺序拼接成message
		 */
		private String headers = "timestamp,nonce,signature";

		/**
		 * 服务端提供的时间戳接口地址
		 */
		private String timestampPath = "/timestamp";

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getHeaders() {
			return headers;
		}

		public void setHeaders(String headers) {
			this.headers = headers;
		}

		public String getTimestampPath() {
			return timestampPath;
		}

		public void setTimestampPath(String timestampPath) {
			this.timestampPath = timestampPath;
		}
	}

	public static class CORS {

		/**
		 * 是否开启跨域 <p>
		 * 网关和微服务层二选一 <p>
		 * 若两者同时存在，会导致响应头中出现多个 Access-Control-Allow-Origin，引发跨域报错
		 */
		private boolean enabled = false;

		/**
		 * 是否允许跨域请求携带 Cookie，前后端必须一致（前端 withCredentials: true） <p>
		 * 当 allow-credentials 为 true 时, allowed-origins 不能设置为 *, 必须指定具体域名, 否则浏览器会拦截响应。
		 */
		private boolean allowCredentials = false;

		/**
		 * 预检请求（OPTIONS）的缓存时间, 单位秒, 为减少预检次数  <p>
		 * 默认 3600秒
		 */
		private Long maxAge = 3600L;

		/**
		 * 允许的跨域源 <p>
		 * 生产环境不建议用 *，需指定具体域名（如 https://xxx.com）<br/>
		 */
		private List<String> allowedOrigins = Arrays.asList("*");

		/**
		 * 允许的跨域请求头, 默认所有都允许
		 */
		private List<String> allowedHeaders = Arrays.asList("*");

		/**
		 * 允许的跨域请求方法, 默认所有都允许
		 */
		private List<String> allowedMethods = Arrays.asList("*");

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public boolean isAllowCredentials() {
			return allowCredentials;
		}

		public void setAllowCredentials(boolean allowCredentials) {
			this.allowCredentials = allowCredentials;
		}

		public Long getMaxAge() {
			return maxAge;
		}

		public void setMaxAge(Long maxAge) {
			this.maxAge = maxAge;
		}

		public List<String> getAllowedOrigins() {
			return allowedOrigins;
		}

		public void setAllowedOrigins(List<String> allowedOrigins) {
			this.allowedOrigins = allowedOrigins;
		}

		public List<String> getAllowedHeaders() {
			return allowedHeaders;
		}

		public void setAllowedHeaders(List<String> allowedHeaders) {
			this.allowedHeaders = allowedHeaders;
		}

		public List<String> getAllowedMethods() {
			return allowedMethods;
		}

		public void setAllowedMethods(List<String> allowedMethods) {
			this.allowedMethods = allowedMethods;
		}
	}

}
