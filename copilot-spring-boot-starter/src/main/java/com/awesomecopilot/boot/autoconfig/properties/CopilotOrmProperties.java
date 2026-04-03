package com.awesomecopilot.boot.autoconfig.properties;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "copilot.orm")
public class CopilotOrmProperties {

	/**
	 * SQL查询逻辑删除相关配置
	 */
	private LogicalDelete logicalDelete = new LogicalDelete();

	private Snowflake snowflake = new Snowflake();

	private Sql sql = new Sql();

	public void setLogicalDelete(LogicalDelete logicalDelete) {
		this.logicalDelete = logicalDelete;
	}

	public LogicalDelete getLogicalDelete() {
		return this.logicalDelete;
	}

	public Snowflake getSnowflake() {
		return snowflake;
	}

	public void setSnowflake(Snowflake snowflake) {
		this.snowflake = snowflake;
	}

	public Sql getSql() {
		return sql;
	}

	public void setSql(Sql sql) {
		this.sql = sql;
	}

	public static class LogicalDelete {

		/**
		 * 是否启用逻辑删除, 一旦启动, 所有查询语句where条件都都会追加deleted=0条件
		 * 如果是复杂SQL查询, 子查询中的where不会自动添加deleted=0, 只会为最外围SQL添加
		 */
		private boolean enabled;

		/**
		 * 数据库表逻辑删除字段名, 默认deleted
		 */
		private String field = "deleted";

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getField() {
			return field;
		}

		public void setField(String field) {
			this.field = field;
		}
	}

	public static class Snowflake {

		/**
		 * 雪花算法的机器ID
		 */
		private int workerId;

		/**
		 * 雪花算法的数据中心ID
		 */
		private int datacenterId;

		public int getWorkerId() {
			return workerId;
		}

		public void setWorkerId(int workerId) {
			this.workerId = workerId;
		}

		public int getDatacenterId() {
			return datacenterId;
		}

		public void setDatacenterId(int datacenterId) {
			this.datacenterId = datacenterId;
		}
	}

	public static class Sql {

		/**
		 * SQL自动修复, 比如自动添加缺失的where关键字, 自动删除多余的and关键字
		 */
		private boolean autoFix;

		/**
		 * 执行批量插入的时候一批的大小
		 */
		private int batchSize = 100;

		public boolean isAutoFix() {
			return autoFix;
		}

		public void setAutoFix(boolean autoFix) {
			this.autoFix = autoFix;
		}

		public int getBatchSize() {
			return batchSize;
		}

		public void setBatchSize(int batchSize) {
			this.batchSize = batchSize;
		}
	}

}