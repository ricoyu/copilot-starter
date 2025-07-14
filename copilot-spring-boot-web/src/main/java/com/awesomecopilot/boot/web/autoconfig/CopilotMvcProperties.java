package com.awesomecopilot.boot.web.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

	private APISign apiSign;

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
}
