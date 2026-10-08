package com.awesomecopilot.cloud.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 金丝雀发布负载均衡所使用的 Nacos 元数据键名配置。
 * <p>
 * {@link com.awesomecopilot.cloud.loadbalancer.CanaryReleaseRule} 依赖实例元数据中的
 * <b>版本号</b>做流量隔离, 这个键名原本写死在代码里, 现在收敛到
 * {@code copilot.discovery.metadata} 下, 不配置时使用与历史行为一致的默认值:
 * <pre>{@code
 * copilot:
 *   discovery:
 *     metadata:
 *       version-key: current-version   # 默认值, 即 spring.cloud.nacos.discovery.metadata.current-version
 * }</pre>
 * 只有当业务方在 Nacos 元数据里用的是别的键名时才需要显式覆盖。
 * <p>
 * 集群名不作为配置项：它由 Spring Cloud Alibaba 标准配置 {@code spring.cloud.nacos.discovery.cluster-name}
 * 决定, 上报到 Nacos 后的元数据键固定为 {@code nacos.cluster}。
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "copilot.discovery.metadata")
public class DiscoveryMetadataProperties {

	/**
	 * 版本号所在的元数据键名, 默认 current-version
	 */
	private String versionKey = "current-version";
}
