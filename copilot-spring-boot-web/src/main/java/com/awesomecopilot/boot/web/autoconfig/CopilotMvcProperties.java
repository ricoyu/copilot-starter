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
}
