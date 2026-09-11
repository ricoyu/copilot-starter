package com.awesomecopilot.cloud.gateway.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * Copyright: (C), 2022-10-27 10:54
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@ConfigurationProperties("copilot.gateway")
public class CopilotGatewayProperties {
	
	/**
	 * 网关认证相关配置
	 */
	private Auth auth = new Auth();

	/**
	 * SpringMVC CORS 配置
	 */
	private CORS cors = new CORS();

	/**
	 * 是否开启Sentinel网关限流, 默认false
	 */
	private Sentinel sentinel = new Sentinel();

	public Auth getAuth() {
		return auth;
	}

	public void setAuth(Auth auth) {
		this.auth = auth;
	}

	public CORS getCors() {
		return cors;
	}

	public void setCors(CORS cors) {
		this.cors = cors;
	}

	@Data
	public static class CORS {

		/**
		 * 是否开启跨域 <p>
		 * 网关和微服务层二选一 <p>
		 * 若两者同时存在，会导致响应头中出现多个 Access-Control-Allow-Origin，引发跨域报错
		 */
		private boolean enabled = true;

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
		 * 这个可以指定多个域名
		 * 允许的跨域源 <p>
		 * 生产环境不建议用 *，需指定具体域名（如 https://xxx.com）<br/>
		 *
		 * 如果前端 www.example.com 跨域请求后端 api.example.com, 那么明确指定allowedOrigin的话应该是www.example.com
		 */
		private List<String> allowedOrigins = Arrays.asList("*");

		/**
		 * 这个是通配符方式指定, 当未指定更具体的allowedOrigins时这个allowedOriginPattern才生效
		 */
		private String allowedOriginPattern = "*";

		/**
		 * 允许的跨域请求头, 默认所有都允许
		 */
		private List<String> allowedHeaders = Arrays.asList("*");

		/**
		 * 允许的跨域请求方法, 默认所有都允许
		 */
		private List<String> allowedMethods = Arrays.asList("*");
	}

	@Data
	public static class Auth {
		
		/**
		 * 指定的URI可以匿名访问
		 */
		private List<String> whiteList = new ArrayList<>();
		
		/**
		 * 是否启用网关层认证
		 */
		private boolean enabled = false;
		
		/**
		 * 如果是JWT token的话, 从认证中心获取公钥的URI, 默认 /oauth/token_key
		 */
		private String tokenKeyEndpoint = "/oauth/token_key";
		
	}

	public Sentinel getSentinel() {
		return sentinel;
	}

	public void setSentinel(Sentinel sentinel) {
		this.sentinel = sentinel;
	}

	public static class Sentinel {

		private boolean enabled;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}
}
