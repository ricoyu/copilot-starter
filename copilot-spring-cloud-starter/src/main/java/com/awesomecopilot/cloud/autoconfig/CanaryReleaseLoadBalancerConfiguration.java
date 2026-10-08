package com.awesomecopilot.cloud.autoconfig;

import com.awesomecopilot.cloud.loadbalancer.CanaryReleaseRule;
import com.awesomecopilot.cloud.properties.DiscoveryMetadataProperties;
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
	                                                              ObjectProvider<DiscoveryMetadataProperties> discoveryMetadataProperties) {
		String serviceId = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
		//没有装配 DiscoveryMetadataProperties 时退化为默认键名 current-version
		return new CanaryReleaseRule(serviceInstanceListSupplier, serviceId,
				discoveryMetadataProperties.getIfAvailable(DiscoveryMetadataProperties::new));
	}

}
