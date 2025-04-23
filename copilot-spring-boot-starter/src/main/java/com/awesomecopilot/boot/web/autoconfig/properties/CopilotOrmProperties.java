package com.awesomecopilot.boot.web.autoconfig.properties;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "copilot.orm")
public class CopilotOrmProperties {

	/**
	 * SQL查询逻辑删除相关配置
	 */
	private LogicalDelete logicalDelete;

	public void setLogicalDelete(LogicalDelete logicalDelete) {
		this.logicalDelete = logicalDelete;
	}

	public LogicalDelete getLogicalDelete() {
		return this.logicalDelete;
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
}
