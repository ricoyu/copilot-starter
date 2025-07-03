package com.awesomecopilot.boot.web.autoconfig;

import com.awesomecopilot.web.listener.ThreadLocalCleanupListener;
import javax.servlet.ServletRequestListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


/**
 *
 * <p>
 * Copyright: (C), 2020/4/14 16:22
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Configuration
@Slf4j
public class CopilotThreadAutoConfiguration {
	
	/**
	 * 在Http请求进来和结束时清理ThreadLocal
	 * @return
	 */
	@Bean
	@ConditionalOnClass(ServletRequestListener.class)
	@Primary
	public ServletListenerRegistrationBean<ServletRequestListener> listenerRegistrationBean() {
		ServletListenerRegistrationBean<ServletRequestListener> bean = new ServletListenerRegistrationBean<>();
		bean.setListener(new ThreadLocalCleanupListener());
		return bean;
	}
}
