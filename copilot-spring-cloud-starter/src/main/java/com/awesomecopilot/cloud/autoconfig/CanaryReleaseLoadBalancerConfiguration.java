package com.awesomecopilot.cloud.autoconfig;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.awesomecopilot.cloud.loadbalancer.CanaryReleaseRule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

public class CanaryReleaseLoadBalancerConfiguration {

	@Bean
	public ReactorServiceInstanceLoadBalancer defaultLoadBalancer(Environment environment,
	                                                              ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplier,
	                                                              NacosDiscoveryProperties nacosDiscoveryProperties) {
		String serviceId = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
		return new CanaryReleaseRule(serviceInstanceListSupplier, serviceId);
	}

}