package com.awesomecopilot.cloud.autoconfig;

import com.awesomecopilot.cloud.properties.DiscoveryMetadataProperties;
import com.awesomecopilot.cloud.properties.LBProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@LoadBalancerClients(
		defaultConfiguration = CanaryReleaseLoadBalancerConfiguration.class
)
@ConditionalOnProperty(name = "copilot.lb.canary-release.enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties({LBProperties.class, DiscoveryMetadataProperties.class})
public class DefaultLBConfig {
}