package com.awesomecopilot.boot.web.filter;

import com.awesomecopilot.common.spring.utils.ServletUtils;
import com.awesomecopilot.web.utils.RestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardServer;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


public class TomcatThreadPoolStatisticFilter extends OncePerRequestFilter {

	@Autowired
	private ApplicationContext applicationContext;

	private static final String pathPrefix = "/tomcat/threadpool";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String requestPath = ServletUtils.requestPath(request);
		if (!ServletUtils.pathMatch(requestPath, pathPrefix)) {
			//filterChain.doFilter(request, response);
			return;
		}

		if (applicationContext instanceof ServletWebServerApplicationContext webAppContext) {
			WebServer webServer = webAppContext.getWebServer();
			if (webServer instanceof TomcatWebServer tomcatWebServer) {

				StandardServer server = (StandardServer) (((TomcatWebServer) webServer).getTomcat().getServer());
				Service[] services = server.findServices();
				if (services.length == 0) {
					return;
				}
				Connector[] connectors = services[0].findConnectors();
				if (connectors.length == 0) {
					return;
				}
				ThreadPoolExecutor executor =
						(ThreadPoolExecutor) connectors[connectors.length - 1].getProtocolHandler().getExecutor();
				if (executor == null) {
					return;
				}
				String threadPool = printThreadPoolInfo((ThreadPoolExecutor) executor);
				RestUtils.writeRawJson(response, threadPool);
				return;
			}
		}
		filterChain.doFilter(request, response);
	}

	private String printThreadPoolInfo(ThreadPoolExecutor executor) {
		String template = """
				{
				  "corePoolSize": {
				    "value": %d,
				    "desc": "核心线程数"
				  },
				  "maximumPoolSize": {
				    "value": %d,
				    "desc": "最大线程数"
				  },
				  "poolSize": {
				    "value": %d,
				    "desc": "当前线程数"
				  },
				  "activeCount": {
				    "value": %d,
				    "desc": "活跃线程数(正在执行任务的线程)"
				  },
				  "completedTaskCount": {
				    "value": %d,
				    "desc": "已完成任务数"
				  },
				  "taskCount": {
				    "value": %d,
				    "desc": "总任务数"
				  },
				  "queueSize": {
				    "value": %d,
				    "desc": "当前队列大小(线程池workQueue里面有多少任务在排队等待被执行)"
				  },
				  "queueRemainingCapacity": {
				    "value": %d,
				    "desc": "队列剩余容量"
				  },
				  "keepAliveTime": {
				    "value": "%d秒",
				    "desc": "保持存活时间"
				  },
				  "allowsCoreThreadTimeOut": {
				    "value": %b,
				    "desc": "允许核心线程超时"
				  }
				}""";
		return String.format(template,
				executor.getCorePoolSize(),
				executor.getMaximumPoolSize(),
				executor.getPoolSize(),
				executor.getActiveCount(),
				executor.getCompletedTaskCount(),
				executor.getTaskCount(),
				executor.getQueue().size(),
				executor.getQueue().remainingCapacity(),
				executor.getKeepAliveTime(TimeUnit.SECONDS),
				executor.allowsCoreThreadTimeOut());
	}

}
