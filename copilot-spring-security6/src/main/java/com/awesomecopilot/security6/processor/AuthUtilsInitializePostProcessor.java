package com.awesomecopilot.security6.processor;

import com.awesomecopilot.cache.auth.AuthUtils;
import com.awesomecopilot.security6.properties.CopilotSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * <p>
 * Copyright: (C), 2020-08-14 17:01
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
public class AuthUtilsInitializePostProcessor implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(AuthUtilsInitializePostProcessor.class);

	@Autowired
	private CopilotSecurityProperties properties;

	@Override
	public void afterSingletonsInstantiated() {
		if (properties.isClearOnStart()) {
			log.info("afterSingletonsInstantiated >> clearOnStart=true, 启动时清理过期token");
			//启动就清理过期token
			AuthUtils.clearExpired();
		} else {
			log.info("afterSingletonsInstantiated >> clearOnStart=false, 延迟清理过期Token");
			//启动先加载AuthUtils, 延迟清理过期Token
			Class<AuthUtils> authUtilsClass = AuthUtils.class;
		}
	}
}
