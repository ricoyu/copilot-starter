package com.awesomecopilot.boot.autoconfig.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <p>
 * Copyright: (C), 2020-09-10 14:27
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@ConfigurationProperties(prefix = "copilot.cache")
public class CopilotCacheProperties {
	
	/**
	 * 是否开启copilot-cahce高级功能支持, 如@RedisListener注解支持
	 */
	private boolean enabled = true;
	
	/**
	 * @CacheEvict 第二删的全局默认延迟秒数; 注解属性 evictDelaySeconds 可按方法覆盖;
	 * 配置为 0 或负数按 1 秒处理
	 */
	private long evictDelaySeconds = 1;
	
	/**
	 * @CacheEvict 第二删允许排队等待的最大任务数, Redis 变慢时超出的第二删被跳过并记录 error;
	 * 配置为 0 或负数按 1000 处理
	 */
	private int evictMaxPendingTasks = 1000;
	
	public boolean isEnabled() {
		return enabled;
	}
	
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	public long getEvictDelaySeconds() {
		return evictDelaySeconds;
	}
	
	public void setEvictDelaySeconds(long evictDelaySeconds) {
		this.evictDelaySeconds = evictDelaySeconds;
	}
	
	public int getEvictMaxPendingTasks() {
		return evictMaxPendingTasks;
	}
	
	public void setEvictMaxPendingTasks(int evictMaxPendingTasks) {
		this.evictMaxPendingTasks = evictMaxPendingTasks;
	}
}