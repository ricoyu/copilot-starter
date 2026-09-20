package com.awesomecopilot.boot.es.autoconfig;

import com.awesomecopilot.common.lang.concurrent.Concurrent;
import com.awesomecopilot.common.lang.utils.IOUtils;
import com.awesomecopilot.search.ElasticUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * <p>
 * Copyright: (C), 2020/4/14 16:22
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@AutoConfiguration
@ConditionalOnClass(ElasticUtils.class)
@ConditionalOnProperty(prefix = "copilot.es", value = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties({CopilotESProperties.class})
@Slf4j
public class CopilotESAutoConfiguration implements InitializingBean {
	
	/**
	 * 从classpath读
	 */
	private static final String CLASSPATH_PREFIX = "classpath:";
	
	/**
	 * 从磁盘绝对路径去读
	 */
	private static final String FILE_SYSTEM_PREFIX = "/";
	
	/**
	 * 有这个符号表示配置了Windows的磁盘文件路径
	 */
	private static final String WINDOWS_FILE_SYSTEM = ":\\";
	
	@Autowired
	private CopilotESProperties properties;
	
	@Autowired
	private Environment environment;
	
	/**
	 * 本配置类受 @ConditionalOnProperty(copilot.es.enabled=true, matchIfMissing=false) 控制:
	 * 必须显式开启才装配, 缺省不配时不加载 ElasticUtils、不建任何 transport 连接;
	 * 开了之后 createMultiFieldAgg 会触发 ElasticUtils 类初始化并建立连接.
	 * 配了本模块其他 copilot.es.* 键但没开开关的场景由
	 * CopilotESUsageGuardAutoConfiguration 在启动日志里点名提示.
	 * (本方法原为 @PostConstruct; 两者都在依赖注入完成后执行、时机等价, 改用 InitializingBean
	 * 只是接口约定更直白, 与评审报告修复记录中的说明一致)
	 */
	@Override
	public void afterPropertiesSet() {
		//保持原语义: 脚本注册与 init 开关无关, 总是执行(该调用会触发 ElasticUtils 类初始化并建立连接)
		ElasticUtils.Cluster.createMultiFieldAgg();
		//不需要启动时初始化Index Template
		if (!properties.isInit()) {
			log.info("copilot.es.init=false, 跳过 Index Template 装载");
			return;
		}
		
		//用集群健康检查替代旧版 existsIndex("ricoyu") 的连通性 ping(不查任何业务索引)
		pingCluster();
		springConfiguredConnectionPropertiesWarnIfShadowed();
		
		if (properties.getSearchMaxBuckets() != null) {
			log.info("设置聚合时桶的最大数量为: {}", properties.getSearchMaxBuckets());
			ElasticUtils.Cluster.settings()
					.persistent()
					.searchMaxBuckets(properties.getSearchMaxBuckets())
					.thenUpdate();
		}
		
		initTemplate(properties.getTemplates());
	}
	
	/**
	 * 装载 Index Template. 每个条目独立 try-catch:
	 * 修复前两处启动崩溃——①文件名无扩展名时 templateName 未赋值即 lastIndexOf, NullPointerException;
	 * ②全部条目空白/缺失时 templatePair 为空, get(0) IndexOutOfBoundsException.
	 * 现在无效条目记 warn/error 后跳过, 全部无效则直接返回.
	 */
	void initTemplate(String[] templates) {
		if (templates == null || templates.length == 0) {
			return;
		}
		
		List<String[]> templatePair = new ArrayList<>();
		for (String templateFileName : templates) {
			if (isBlank(templateFileName)) {
				log.warn("copilot.es.templates 含空白条目, 已跳过");
				continue;
			}
			
			String content;
			try {
				content = readTemplateContent(templateFileName.trim());
			} catch (Exception e) {
				log.error("读取 Index Template 文件失败, 跳过该条目: {}", templateFileName, e);
				continue;
			}
			if (isBlank(content)) {
				log.error("Index Template 文件不存在或内容为空, 跳过该条目: {} (检查文件路径与扩展名)", templateFileName);
				continue;
			}
			
			String templateName = resolveTemplateName(templateFileName.trim());
			if (templateName == null) {
				log.error("Index Template 文件名解析不出模板名, 跳过该条目: {}", templateFileName);
				continue;
			}
			templatePair.add(new String[]{templateName, content});
		}
		
		if (templatePair.isEmpty()) {
			log.info("copilot.es.templates 配置了 {} 项, 但没有可装载的模板", templates.length);
			return;
		}
		
		//配置初始化多个Index Template时走并发. 写入失败策略与单模板一致: 抛出使启动失败,
		//并带上模板名定位(评审建议5: 不再出现"单模板启动失败、多模板只记日志"的分歧)
		if (templatePair.size() > 1) {
			List<String> failed = Collections.synchronizedList(new ArrayList<>());
			templatePair.forEach((nameAndContent) -> {
				Concurrent.execute(() -> {
					try {
						putIndexTemplate(nameAndContent[0], nameAndContent[1]);
					} catch (Exception e) {
						failed.add(nameAndContent[0]);
						log.error("写入 Index Template [{}] 失败", nameAndContent[0], e);
					}
				});
			});
			Concurrent.await();
			if (!failed.isEmpty()) {
				throw new IllegalStateException("Index Template 写入失败: " + String.join(",", failed)
					+ " —— 详见上方带模板名的 error 日志");
			}
		} else {
			String[] nameAndContent = templatePair.get(0);
			try {
				putIndexTemplate(nameAndContent[0], nameAndContent[1]);
			} catch (RuntimeException e) {
				throw new IllegalStateException("Index Template 写入失败: " + nameAndContent[0] + ", 原因: " + e.getMessage(), e);
			}
		}
		log.info("Put Index Template Done!");
	}
	
	/**
	 * 从模板文件路径解析模板名: 去掉读取前缀与目录部分, 再去掉扩展名.
	 * 文件名无扩展名时整段作为模板名(修复旧版此处 NullPointerException);
	 * 点号在首字符的输入(如 ".json")剥扩展名后 basename 为空, 返回 null, 由调用方按无效条目跳过.
	 */
	static String resolveTemplateName(String templateFileName) {
		String name = templateFileName;
		//去读取前缀: classpath:/ classpath:: 以及盘符
		if (name.startsWith(CLASSPATH_PREFIX)) {
			name = name.substring(CLASSPATH_PREFIX.length());
			if (name.startsWith("/")) {
				name = name.substring(1);
			}
		}
		//去目录部分(兼容 / \ 两种分隔符取最后一段)
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		if (slash != -1) {
			name = name.substring(slash + 1);
		}
		//Windows 盘符残留(理论上目录已剥离, 防御 ":\" 之外的 "C:" 形式)
		int colon = name.lastIndexOf(':');
		if (colon != -1 && colon < name.length() - 1) {
			name = name.substring(colon + 1);
		}
		//去扩展名. dot==0(如 ".json")时 basename 为空, 整体判 null, 不允许以 ".json" 这类怪名字写入 ES
		int dot = name.lastIndexOf('.');
		if (dot != -1) {
			name = name.substring(0, dot);
		}
		return isBlank(name) ? null : name;
	}
	
	/**
	 * 显式指定 classpath:/文件系统绝对路径则按前缀读; 否则按 config/ → 工作目录 → classpath 顺序读.
	 * 读取语义与旧版保持一致, 仅不再依赖其结果非空.
	 */
	private String readTemplateContent(String templateFileName) {
		if (templateFileName.startsWith(CLASSPATH_PREFIX)) {
			return IOUtils.readClassPathFileAsString(templateFileName);
		}
		if (templateFileName.startsWith(FILE_SYSTEM_PREFIX) || templateFileName.contains(WINDOWS_FILE_SYSTEM)) {
			return IOUtils.readFileAsString(templateFileName);
		}
		String workingDir = System.getProperty("user.dir");
		String fileSep = System.getProperty("file.separator");
		String content = IOUtils.readFileAsString(workingDir + fileSep + "config" + fileSep + templateFileName);
		if (isBlank(content)) {
			content = IOUtils.readFileAsString(workingDir + fileSep + templateFileName);
		}
		if (isBlank(content)) {
			content = IOUtils.readClassPathFileAsString(templateFileName);
		}
		return content;
	}
	
	/** 写入单个 Index Template 的接缝, 测试覆写以隔离真实 ES */
	protected void putIndexTemplate(String templateName, String content) {
		ElasticUtils.Admin.putIndexTemplate(templateName, content);
	}
	
	/** 连通性检查接缝: 旧版为 existsIndex("ricoyu")(硬编码个人昵称), 现改查集群健康 */
	protected void pingCluster() {
		log.info("Elasticsearch 集群健康状态: {}", ElasticUtils.Cluster.health());
	}
	
	/**
	 * 配置通道割裂的显式提示(修复报告 P2-3 的可行部分):
	 * transport 连接参数(cluster.name/elastic.hosts/elastic.username/elastic.password)
	 * 由底层 ElasticUtils 静态初始化读取 classpath:elastic.properties, 不走 Spring Environment;
	 * 用户若在 application.yml 里配 copilot.es.* 之外的 spring.elasticsearch.* 或把上述键配成
	 * Spring 属性, 会看起来生效实则被忽略. 这里在启动日志里点名, 避免排查绕远路.
	 */
	private void springConfiguredConnectionPropertiesWarnIfShadowed() {
		String[] shadowKeys = {
			// Boot 官方 ES 键(本模块 transport 客户端不读)
			"spring.elasticsearch.uris", "spring.elasticsearch.username", "spring.elasticsearch.password",
			// 最可能的主误配通道: 把 elastic.properties 的键写进 application.yml/配置中心
			"cluster.name", "elastic.hosts", "elastic.rest.hosts", "elastic.username", "elastic.password",
			// 尚不存在的 copilot.es.* 连接键(防用户按其他 starter 的习惯猜测)
			"copilot.es.hosts", "copilot.es.username", "copilot.es.password"
		};
		for (String key : shadowKeys) {
			if (environment.containsProperty(key)) {
				log.warn("检测到 Spring 属性 {} 已配置, 但 transport 客户端只读取 classpath:elastic.properties"
					+ " (同名文件可放在 工作目录/elastic.properties 或 工作目录/config/elastic.properties,"
					+ " 优先级从低到高: classpath < 工作目录 < 工作目录/config),"
					+ " 该 Spring 属性不会生效; 需要 Spring 化配置请等待模块迁移到 ES 8.x Java Client", key);
			}
		}
	}
}
