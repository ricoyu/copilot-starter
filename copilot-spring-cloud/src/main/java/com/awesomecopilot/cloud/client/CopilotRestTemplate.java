package com.awesomecopilot.cloud.client;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.observation.ClientHttpObservationDocumentation;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.ClientRequestObservationConvention;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.util.Assert;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Random;

/**
 * 支持在服务启动过程中从Nacos拉取服务列表然后调用
 * <p/>
 * Copyright: Copyright (c) 2025-04-21 14:56
 * <p/>
 * Company: Sexy Uncle Inc.
 * <p/>

 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
@Slf4j
public class CopilotRestTemplate extends RestTemplate {

	private static final ClientRequestObservationConvention
			DEFAULT_OBSERVATION_CONVENTION = new DefaultClientRequestObservationConvention();

	private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
	private DiscoveryClient discoveryClient;

	public CopilotRestTemplate(DiscoveryClient discoveryClient) {
		this.discoveryClient = discoveryClient;
	}

	@Override
	protected <T> T doExecute(URI url, String uriTemplate,
	                          HttpMethod method, RequestCallback requestCallback,
	                          ResponseExtractor<T> responseExtractor) throws RestClientException {
		Assert.notNull(url, "url is required");
		Assert.notNull(method, "HttpMethod is required");

		ClientHttpRequest request;
		try {
			log.info("请求的url路径为:{}", url);
			//把服务名 替换成我们的IP
			url = replaceUrl(url);
			log.info("替换后的路径:{}", url);
			request = createRequest(url, method);
		}
		catch (IOException ex) {
			throw createResourceAccessException(url, method, ex);
		}

		ClientRequestObservationContext observationContext = new ClientRequestObservationContext(request);
		observationContext.setUriTemplate(uriTemplate);
		Observation observation = ClientHttpObservationDocumentation.HTTP_CLIENT_EXCHANGES.observation(
				getObservationConvention(), DEFAULT_OBSERVATION_CONVENTION,
				() -> observationContext, this.observationRegistry).start();
		ClientHttpResponse response = null;
		try (Observation.Scope scope = observation.openScope()){
			if (requestCallback != null) {
				requestCallback.doWithRequest(request);
			}
			response = request.execute();
			observationContext.setResponse(response);
			handleResponse(url, method, response);
			return (responseExtractor != null ? responseExtractor.extractData(response) : null);
		}
		catch (IOException ex) {
			ResourceAccessException accessEx = createResourceAccessException(url, method, ex);
			observation.error(accessEx);
			throw accessEx;
		}
		catch (Throwable ex) {
			observation.error(ex);
			throw ex;
		}
		finally {
			if (response != null) {
				response.close();
			}
			observation.stop();
		}
	}

	private URI replaceUrl(URI url) {
		//1:从URI中解析调用的服务名,如: product-center
		String serviceName = url.getHost();
		log.info("调用微服务的名称:{}", serviceName);

		//2:解析我们的请求路径 如: /selectProductInfoById/1
		String path = url.getPath();
		log.info("请求path:{}", path);

		//通过微服务的名称去nacos服务端获取 对应的实例列表
		List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
		if (instances.isEmpty()) {
			throw new RuntimeException("没有可用的微服务实例列表:" + serviceName);
		}

		String serviceIp = chooseTargetIp(instances);
		String source = serviceIp + path;
		try {
			return new URI(source);
		} catch (URISyntaxException e) {
			log.error("根据source:{}构建URI异常", source);
		}
		return url;
	}


	/**
	 * 从服务列表中 随机选举一个ip
	 * @param instances
	 * @return
	 */
	private String chooseTargetIp(List<ServiceInstance> instances) {
		//采取随机的获取一个
		Random random = new Random();
		Integer randomIndex = random.nextInt(instances.size());
		String serviceIp = instances.get(randomIndex).getUri().toString();
		log.info("随机选举的服务IP:{}", serviceIp);
		return serviceIp;
	}

	private static ResourceAccessException createResourceAccessException(URI url, HttpMethod method, IOException ex) {
		String resource = url.toString();
		resource = (url.getRawQuery() != null ? resource.substring(0, resource.indexOf('?')) : resource);
		return new ResourceAccessException("I/O error on " + method.name() +
				" request for \"" + resource + "\": " + ex.getMessage(), ex);
	}
}
