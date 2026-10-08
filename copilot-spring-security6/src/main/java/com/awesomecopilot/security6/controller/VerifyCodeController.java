package com.awesomecopilot.security6.controller;

import com.awesomecopilot.cache.JedisUtils;
import com.awesomecopilot.common.lang.utils.StringUtils;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.security6.constants.SecurityConstants;
import com.awesomecopilot.security6.properties.CopilotSecurityProperties;
import com.awesomecopilot.security6.utils.VerifyCodeUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import static com.awesomecopilot.common.lang.utils.StringUtils.concat;
import static com.awesomecopilot.security6.constants.SecurityConstants.PIC_CODE_URL;
import static com.awesomecopilot.security6.constants.SecurityConstants.VERIFY_CODE_PREFIX;
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
	public Result verificationCode(HttpServletRequest request) {
		log.info("verificationCode 开始");
		//图片验证码唯一ID
		String codeId = StringUtils.uniqueKey(12);
		//生成随机字串
		String verifyCode = VerifyCodeUtils.generateVerifyCode(4);
		//生成图片
		String base64Encoded = VerifyCodeUtils.outputImage(verifyCode);
		
		CopilotSecurityProperties.Feature.PicCode picCode = properties.getFeature().getPicCode();
		//评审 P1-2: 存储值绑定生成者 IP(形态 "{ip}:{码}"), 校验端拒绝别机复用;
		//经代理时 IP 取 X-Forwarded-For 首段, 该值可被伪造, 这是提高复用成本而非绝对防线
		String valueWithIp = clientIp(request) + ":" + verifyCode;
		//放到Redis, 有效期由 pic-code.ttl 配置
		storeCode(concat(VERIFY_CODE_PREFIX, codeId).toLowerCase(), valueWithIp, picCode.getTtl());
		
		Map<String, Object> results = new HashMap<>(2);
		results.put(SecurityConstants.VERIFY_CODE_ID, codeId);
		results.put(SecurityConstants.VERIFY_CODE, base64Encoded);
		
		log.info("verificationCode 结束, codeId={}, ttl={}分钟", codeId, picCode.getTtl());
		return Results.success().data(results).build();
	}
	
	/** Redis 写入接缝(protected 便于单测覆写) */
	protected void storeCode(String key, String valueWithIpPrefix, long ttlMinutes) {
		JedisUtils.set(key, valueWithIpPrefix, ttlMinutes, MINUTES);
	}
	
	/** 客户端真实 IP 接缝 */
	protected String clientIp(HttpServletRequest request) {
		return ServletUtils.getRemoteRealIP(request);
	}
}
