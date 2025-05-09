package com.awesomecopilot.boot.es.autoconfig;

import com.awesomecopilot.common.lang.concurrent.Concurrent;
import com.awesomecopilot.common.lang.utils.IOUtils;
import com.awesomecopilot.search.ElasticUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
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
@EnableConfigurationProperties({CopilotESProperties.class})
@Configuration
@Slf4j
public class CopilotESAutoConfiguration {
	
	private static final String WORKING_DIR = System.getProperty("user.dir");
	
	private static final String FILE_SEPRATOR = System.getProperty("file.separator");
	
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
	
	@PostConstruct
	public void init() {
		ElasticUtils.Cluster.createMultiFieldAgg();
		//不需要启动时初始化Index Template
		if (!properties.isInit()) {
			return;
		}
		
		if (properties.getSearchMaxBuckets() != null) {
			log.info("设置聚合时桶的最大数量为: {}", properties.getSearchMaxBuckets());
			ElasticUtils.Cluster.settings()
					.persistent()
					.searchMaxBuckets(properties.getSearchMaxBuckets())
					.thenUpdate();
		}
		
		String[] templates = properties.getTemplates();
		//没有配置templateName和templateFileName, 也不需要初始化
		if (templates == null || templates.length == 0) {
			return;
		}
		
		//用检查index存在与否来实现初始化客户端连接的目的
		ElasticUtils.Admin.existsIndex("ricoyu");
		
		List<String[]> templatePair = new ArrayList<>();
		for (int i = 0; i < templates.length; i++) {
			String templateFileName = templates[i];
			
			if (isBlank(templateFileName)) {
				continue;
			}
			
			String content = null;
			//显式指定从classpath下读
			if (templateFileName.startsWith(CLASSPATH_PREFIX)) {
				content = IOUtils.readClassPathFileAsString(templateFileName);
			} else if (templateFileName.startsWith(FILE_SYSTEM_PREFIX) || templateFileName.contains(WINDOWS_FILE_SYSTEM)) {
				// 显式指定从文件系统下读
				content = IOUtils.readFileAsString(templateFileName);
			} else {
				/*
				 * 如果没有指定前缀的话, 按照优先级从高到低顺序读取
				 * 1. 当前工作目录/config/ 下读取
				 * 2. 当前工作目录
				 * 3. classpath
				 */
				content = IOUtils.readFileAsString(WORKING_DIR + FILE_SEPRATOR + "config" + FILE_SEPRATOR + templateFileName);
				if (isBlank(content)) {
					content = IOUtils.readFileAsString(WORKING_DIR + FILE_SEPRATOR + templateFileName);
					if (isBlank(content)) {
						content = IOUtils.readClassPathFileAsString(templateFileName);
					}
				}
			}
			
			String templateName = null;
			int dotIndex = templateFileName.lastIndexOf(".");
			if (dotIndex != -1) {
				templateName = templateFileName.substring(0, dotIndex);
			}
			int slashIndex = templateName.lastIndexOf("\\");
			if (slashIndex != -1 && templateName.length() > 1) {
				templateName = templateName.substring(slashIndex + 1);
			}
			int backSlashIndex = templateName.lastIndexOf("/");
			if (backSlashIndex != -1 && templateName.length() > 1) {
				templateName = templateName.substring(backSlashIndex + 1);
			}
			int colonIndex = templateName.lastIndexOf(":");
			if (colonIndex != -1 && templateName.length() > 1) {
				templateName = templateName.substring(colonIndex + 1);
			}
			templatePair.add(new String[]{templateName, content});
		}
		
		//配置初始化多个Index Template时走并发
		if (templatePair.size() > 1) {
			templatePair.forEach((nameAndContent) -> {
				Concurrent.execute(() -> ElasticUtils.Admin.putIndexTemplate(nameAndContent[0], nameAndContent[1]));
			});
			Concurrent.await();
		} else {
			String[] nameAndContent = templatePair.get(0);
			ElasticUtils.Admin.putIndexTemplate(nameAndContent[0], nameAndContent[1]);
		}
		log.info("Put Index Template Done!");
	}
}
