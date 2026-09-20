package com.awesomecopilot.boot.es.autoconfig;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * CopilotESAutoConfiguration 索引模板装载的纯逻辑测试（评审报告 P1-1）.
 * <p>
 * 通过覆写 putIndexTemplate 接缝隔离真实 ES 连接, 只验证:
 * 模板文件名解析、缺扩展名/文件读不到/内容为空时跳过并记日志、不再抛 NPE/IndexOutOfBounds.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class CopilotESAutoConfigurationTest {
	
	/** 记录本应写入 ES 的模板、不真正连接的测试子类 */
	static class RecordingConfig extends CopilotESAutoConfiguration {
		final List<String> puts = new ArrayList<>();
		
		@Override
		protected void putIndexTemplate(String templateName, String content) {
			puts.add(templateName + "=" + content.trim());
		}
		
		@Override
		protected void pingCluster() {
			//测试不连集群
		}
	}
	
	@Test
	void templateFileNameWithoutExtensionIsResolvedNotNpe() {
		// 修复前: 文件名不含 '.' 时 templateName 未赋值, lastIndexOf 抛 NullPointerException
		assertThatCode(() -> CopilotESAutoConfiguration.resolveTemplateName("classpath:templates/no_ext"))
			.doesNotThrowAnyException();
	}
	
	@Test
	void resolvesTemplateNameFromVariousPathStyles() {
		assertThat(CopilotESAutoConfiguration.resolveTemplateName("classpath:event_template.json"))
			.isEqualTo("event_template");
		assertThat(CopilotESAutoConfiguration.resolveTemplateName("/root/event_template.json"))
			.isEqualTo("event_template");
		assertThat(CopilotESAutoConfiguration.resolveTemplateName("D:\\config\\event_template.json"))
			.isEqualTo("event_template");
		//无扩展名也取整段文件名(不含路径/前缀)作为模板名
		assertThat(CopilotESAutoConfiguration.resolveTemplateName("classpath:templates/no_ext"))
			.isEqualTo("no_ext");
		//独立评审点名的怪输入: 盘符冒号、双冒号前缀、版本号目录(旧版会错切成 "v1")
		assertThat(CopilotESAutoConfiguration.resolveTemplateName("C:event.json")).isEqualTo("event");
		assertThat(CopilotESAutoConfiguration.resolveTemplateName("classpath::weird.json")).isEqualTo("weird");
		assertThat(CopilotESAutoConfiguration.resolveTemplateName("/etc/v1.2/event")).isEqualTo("event");
		//点号开头的文件名: basename 为空 → null, 调用方跳过
		assertThat(CopilotESAutoConfiguration.resolveTemplateName("classpath:templates/.json")).isNull();
	}
	
	@Test
	void initTemplateSkipsMissingAndBlankEntriesWithoutTouchingEs() {
		RecordingConfig config = new RecordingConfig();
		
		// 修复前: 全部条目无效时 templatePair 为空, templatePair.get(0) 抛 IndexOutOfBoundsException;
		// 单个模板缺失(读不到内容)也不允许让启动失败
		assertThatCode(() -> config.initTemplate(new String[]{
			"",                                                     // 空白条目
			"classpath:templates/does_not_exist.json",              // 读不到
			"classpath:templates/blank.json"                        // 内容全空白
		})).doesNotThrowAnyException();
		
		assertThat(config.puts).isEmpty();
	}
	
	@Test
	void initTemplatePutsValidTemplatesWithResolvedNames() {
		RecordingConfig config = new RecordingConfig();
		
		config.initTemplate(new String[]{
			"classpath:templates/no_ext.tmpl",
			"classpath:templates/does_not_exist.json",
			"classpath:templates/blank.json"
		});
		
		assertThat(config.puts).containsExactly(
			"no_ext=" + "{\"index_patterns\": [\"unit_test_no_ext-*\"]}");
	}
	
	@Test
	void degenerateTemplateFileNameIsSkippedNotSentToEs() {
		//"classpath:.json" 去前缀后是 ".json", 剥扩展名条件(dot>0)不成立 → 名字为空,
		//这种条目应视为无效跳过, 不允许以怪名字写入 ES(评审必修项4: javadoc 的 null 契约要在调用方兑现)
		RecordingConfig config = new RecordingConfig();
		//夹具 .json 真实存在且内容非空白, 排除"读不到"分支, 专测名字退化分支:
		//修复前 resolveTemplateName(".json") 返回 ".json"(dot 在下标 0 不去扩展名), 会以怪名字写入 ES
		config.initTemplate(new String[]{"classpath:templates/.json"});
		
		assertThat(config.puts).isEmpty();
	}
	
	@Test
	void multipleValidTemplatesAllWrittenViaConcurrentBranch() {
		//并发分支(size>1)回归: 两个有效模板都要写入
		RecordingConfig config = new RecordingConfig();
		
		config.initTemplate(new String[]{
			"classpath:templates/no_ext.tmpl",
			"classpath:templates/second.json"
		});
		
		assertThat(config.puts).hasSize(2)
			.contains("no_ext=" + "{\"index_patterns\": [\"unit_test_no_ext-*\"]}")
			.anyMatch(s -> s.startsWith("second="));
	}
	
	@Test
	void writeFailureOfSingleTemplatePropagatesAndFailsStartup() {
		//写入失败策略统一为"抛出使启动失败"(评审建议5采纳):
		//单模板与多模板行为一致, 不再出现"单模板启动失败、多模板只记日志"的分歧
		class FailingConfig extends CopilotESAutoConfiguration {
			@Override
			protected void putIndexTemplate(String templateName, String content) {
				throw new RuntimeException("simulated es write failure");
			}
			
			@Override
			protected void pingCluster() {
			}
		}
		
		org.assertj.core.api.Assertions.assertThatThrownBy(
			() -> new FailingConfig().initTemplate(new String[]{"classpath:templates/no_ext.tmpl"}))
			.hasMessageContaining("no_ext")
			.rootCause().hasMessageContaining("simulated es write failure");
	}
	
	@Test
	void writeFailureOfMultipleTemplatesAlsoPropagatesWithTemplateName() {
		//并发分支(size>1)的写入失败同样向上传动, 且异常/日志带模板名(评审建议5)
		class FailingConfig extends CopilotESAutoConfiguration {
			@Override
			protected void putIndexTemplate(String templateName, String content) {
				throw new RuntimeException("simulated es write failure for " + templateName);
			}
			
			@Override
			protected void pingCluster() {
			}
		}
		
		org.assertj.core.api.Assertions.assertThatThrownBy(
			() -> new FailingConfig().initTemplate(new String[]{
				"classpath:templates/no_ext.tmpl", "classpath:templates/second.json"}))
			.hasMessageContaining("no_ext")
			.hasMessageContaining("second");  //失败模板名要能在异常信息里定位到
	}
}
