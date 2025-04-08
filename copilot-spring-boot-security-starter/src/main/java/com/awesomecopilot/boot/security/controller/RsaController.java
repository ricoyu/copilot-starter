package com.awesomecopilot.boot.security.controller;

import com.awesomecopilot.codec.RsaUtils;
import com.awesomecopilot.common.lang.vo.Result;
import com.awesomecopilot.common.lang.vo.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * Copyright: (C), 2021-05-21 10:28
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@RestController
public class RsaController {
	
	@GetMapping("/public-key")
	public Result publicKey() {
		return Results.success().data(RsaUtils.publicKeyStr()).build();
	}
}
