package com.awesomecopilot.security.controller;

import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.common.lang.utils.StringUtils;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.security.constants.SecurityConstants;
import com.awesomecopilot.security.properties.CopilotSecurityProperties;
import com.awesomecopilot.security.utils.VerifyCodeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import static com.awesomecopilot.common.lang.utils.StringUtils.concat;
import static com.awesomecopilot.security.constants.SecurityConstants.PIC_CODE_URL;
import static com.awesomecopilot.security.constants.SecurityConstants.VERIFY_CODE_PREFIX;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * <p>
 * Copyright: (C), 2021-05-17 13:49
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Slf4j
@RestController
public class VerifyCodeController {
	
	@Autowired
	private CopilotSecurityProperties properties;
	
	@GetMapping(PIC_CODE_URL)
	public Result verificationCode() {
		//图片验证码唯一ID
		String codeId = StringUtils.uniqueKey(12);
		//生成随机字串
		String verifyCode = VerifyCodeUtils.generateVerifyCode(4);
		//生成图片
		String base64Encoded = VerifyCodeUtils.outputImage(verifyCode);
		
		CopilotSecurityProperties.Feature.PicCode picCode = properties.getFeature().getPicCode();;
		//放到Redis, 5分钟有效期
		JedisUtils.set(concat(VERIFY_CODE_PREFIX, codeId).toLowerCase(), verifyCode, picCode.getTtl(), MINUTES);
		
		Map<String, Object> results = new HashMap<>(2);
		results.put(SecurityConstants.VERIFY_CODE_ID, codeId);
		results.put(SecurityConstants.VERIFY_CODE, base64Encoded);
		
		return Results.success().data(results).build();
	}
	
}
