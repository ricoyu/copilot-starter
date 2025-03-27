package com.awesomecopilot.boot.web.controller;

import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.common.lang.utils.StringUtils;
import com.awesomecopilot.boot.web.autoconfig.CopilotMvcProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * Copyright: (C), 2022-01-21 17:18
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@RestController
public class IdempotentTokenController {
	
	@Autowired
	private CopilotMvcProperties properties;
	
	@GetMapping("/idempotent-token")
	public String idempotentToken() {
		String idempotentToken = StringUtils.uniqueKey(36).toLowerCase();
		JedisUtils.HASH.hset("idempotent-token", idempotentToken, "", properties.getIdemtotentTokenTtl());
		return idempotentToken;
	}
}
