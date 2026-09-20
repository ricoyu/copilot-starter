package com.awesomecopilot.boot.es.autoconfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <p>
 * Copyright: (C), 2020/4/23 12:52
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "copilot.es")
public class CopilotESProperties {
	
	/**
	 * 总开关: 必须显式配 true 才装配 ES 自动配置(连接/索引模板); 缺省不配时不建任何连接.
	 * 配了其他 copilot.es.* 使用项但忘了开本开关时, 启动日志会收到点名提示
	 * (CopilotESUsageGuardAutoConfiguration). 实际判定由 @ConditionalOnProperty 读 Environment 完成,
	 * 本字段仅为 IDE 生成配置提示
	 */
	private boolean enabled = false;
	
	/**
	 * 是否启动时初始化Elasticsearch客户端连接
	 */
	private boolean init = true;
	
	/**
	 * 聚合时, 桶的最大数量
	 */
	private Integer searchMaxBuckets;
	
	/**
	 * 将定义好的 Index Template 写到指定的文件里面
	 * 文件可以从classpath下读, 也可以从文件系统下读
	 * <ol>
	 *     <li/>classpath:event_template.json 从classpath根目录下读
	 *     <li/>event_template.json           从work dir下读
	 *     <li/>/root/event_template.json     从指定目录下读
	 * </ol>
	 */
	private String[] templates;
	
}
