package com.awesomecopilot.boot.autoconfig.properties;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "copilot.snowflake")
public class CopilotOrmSnowflakeProperties {

	/**
	 * 雪花算法workerId, 默认1
	 */
	private Integer workerId = 1;

	/**
	 *雪花算法datacenterId, 默认1
	 */
	private Integer datacenterId = 1;

	public Integer getWorkerId() {
		return workerId;
	}

	public void setWorkerId(Integer workerId) {
		this.workerId = workerId;
	}

	public Integer getDatacenterId() {
		return datacenterId;
	}

	public void setDatacenterId(Integer datacenterId) {
		this.datacenterId = datacenterId;
	}
}