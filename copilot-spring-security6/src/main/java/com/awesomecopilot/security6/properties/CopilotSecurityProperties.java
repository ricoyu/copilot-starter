package com.awesomecopilot.security6.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Copyright: (C), 2021-04-01 16:06
 * <p>
 * <p>
 * Company: Information & Data Security Solutions Co., Ltd.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@ConfigurationProperties(prefix = "copilot.security6")
public class CopilotSecurityProperties {
	
	/**
	 * 是否启动就执行过期token清理
	 */
	private boolean clearOnStart = false;

	/**
	 * SpringBoot应用的ContentPath或者是Nginx那边反向代理配置的location前缀
	 */
	private String contextPath;
	
	/**
	 * 指定的URI可以匿名访问
	 */
	private List<String> whiteList = new ArrayList<>();

	/**
	 * 用户名密码登录功能配置
	 */
	private UsernamePasswordLogin userPassLogin = new UsernamePasswordLogin();

	private Feature feature = new Feature();

	public boolean isClearOnStart() {
		return clearOnStart;
	}

	public void setClearOnStart(boolean clearOnStart) {
		this.clearOnStart = clearOnStart;
	}

	public List<String> getWhiteList() {
		return whiteList;
	}

	public void setWhiteList(List<String> whiteList) {
		this.whiteList = whiteList;
	}

	public Feature getFeature() {
		return feature;
	}

	public void setFeature(Feature feature) {
		this.feature = feature;
	}

	public UsernamePasswordLogin getUserPassLogin() {
		return userPassLogin;
	}

	public void setUserPassLogin(UsernamePasswordLogin userPassLogin) {
		this.userPassLogin = userPassLogin;
	}

	public static class Feature {

		/**
		 * 是否要集成图片验证码功能
		 */
		private PicCode picCode;

		/**
		 * 是否要开启防止重复提交, 开始@AntiDupSubmit注解支持
		 */
		private boolean antiDuplicateSubmit = true;

		/**
		 * 是否要对应用限流, 开启@RateLimit注解支持
		 */
		private boolean rateLimit = true;

		public boolean isRateLimit() {
			return rateLimit;
		}

		public void setRateLimit(boolean rateLimit) {
			this.rateLimit = rateLimit;
		}

		public boolean isAntiDuplicateSubmit() {
			return antiDuplicateSubmit;
		}

		public void setAntiDuplicateSubmit(boolean antiDuplicateSubmit) {
			this.antiDuplicateSubmit = antiDuplicateSubmit;
		}

		public PicCode getPicCode() {
			return picCode;
		}

		public void setPicCode(PicCode picCode) {
			this.picCode = picCode;
		}

		public static class PicCode {

			/**
			 * 是否要集成验证码功能, 访问URL /pic-code
			 */
			private boolean enabled = false;

			/**
			 * 图片验证码多久过期, 默认5分钟
			 */
			private long ttl = 5;

			public boolean isEnabled() {
				return enabled;
			}

			public void setEnabled(boolean enabled) {
				this.enabled = enabled;
			}

			public long getTtl() {
				return ttl;
			}

			public void setTtl(long ttl) {
				this.ttl = ttl;
			}
		}

	}

	public static class UsernamePasswordLogin {

		/**
		 * 认证中心提供基于用户名密码方式的登录功能, 如果启用, 用户需要注册一个UserDetailsService实现
		 */
		private boolean enabled = false;

		/**
		 * 如果开启用户名密码认证, 配置登录URL, 默认 /login
		 */
		private String loginUrl = "/login";

		/**
		 * 如果开启用户名密码认证, 配置登出URL, 默认 /logout
		 */
		private String logoutUrl = "/logout";

		/**
		 * Spring Security的RoleVoter在投票的时候, 会检查@Secured("ROLE_ADMIN")等注解上是否写了ROLE_前缀<p>
		 * 如果不想要加ROLE_前缀或者想配置另外一个前缀, 可以通过这个配置项来指定
		 */
		private String rolePrefix = "ROLE_";

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getRolePrefix() {
			return rolePrefix;
		}

		public void setRolePrefix(String rolePrefix) {
			this.rolePrefix = rolePrefix;
		}

		public String getLogoutUrl() {
			return logoutUrl;
		}

		public void setLogoutUrl(String logoutUrl) {
			this.logoutUrl = logoutUrl;
		}

		public String getLoginUrl() {
			return loginUrl;
		}

		public void setLoginUrl(String loginUrl) {
			this.loginUrl = loginUrl;
		}
	}
}
