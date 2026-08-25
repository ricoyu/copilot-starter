package com.awesomecopilot.cloud.loadbalancer;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.awesomecopilot.common.lang.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 金丝雀发布（Canary Release）自定义负载均衡规则，实现基于 Nacos 元数据的多级实例选择策略。
 * <p>
 * 该类实现了 {@link ReactorServiceInstanceLoadBalancer} 接口，在微服务间调用时根据当前服务实例的
 * {@code current-version} 元数据与目标实例的元数据进行多级匹配，从而在多人协作开发环境中实现
 * <b>开发者级别的流量隔离</b>，确保每位开发者的本地请求链路闭环在自己的实例之间。
 * <p>
 * <b>────────────────────────────────────────────────────────</b><br>
 * <b>多人协作开发环境下的流量隔离</b>
 * <b>────────────────────────────────────────────────────────</b>
 * <p>
 * <b>核心隔离机制：</b>流量隔离主要依赖 Nacos 元数据中的 {@code current-version} 字段。
 * 每位开发者在本地配置一个<b>携带个人标识的版本号</b>（而非传统的语义化版本号），使得各开发者的本地实例
 * 在 Nacos 注册中心中拥有互不相同的 {@code current-version}，从而实现请求路由的自动隔离。
 * <p>
 * <b>注意：</b>{@code cluster-name} 用于标识 Nacos 集群归属，<b>不作为</b>开发者本地实例的唯一区分标识。
 * 即使多位开发者处于同一 Nacos 集群，只要 {@code current-version} 不同，流量就不会互相串扰。
 * <p>
 * <b>配置示例：</b>
 * <pre>{@code
 * # 开发者 Rico 的本地 application-dev.yml
 * spring:
 *   cloud:
 *     nacos:
 *       discovery:
 *         metadata:
 *           current-version: rico-1.0.0    # 携带个人标识的版本号
 *
 * # 开发者 Paul 的本地 application-dev.yml
 * spring:
 *   cloud:
 *     nacos:
 *       discovery:
 *         metadata:
 *           current-version: paul-1.0.0    # 携带个人标识的版本号
 *
 * # 线上公共环境
 * spring:
 *   cloud:
 *     nacos:
 *       discovery:
 *         metadata:
 *           current-version: 1.0.0         # 标准语义化版本号
 * }</pre>
 * <b>隔离原理：</b>当开发者 Rico 的本地服务 A 调用下游服务 B 时，本规则会从 Nacos 获取服务 B 的所有实例列表，
 * 并按以下三级策略逐级匹配：
 * <ol>
 *   <li><b>同集群同版本（最优）</b> — 筛选 {@code nacos.cluster} 相同且 {@code current-version == "rico-1.0.0"}
 *       的实例。由于 Paul 的实例版本号为 {@code "paul-1.0.0"}，不匹配，被自然排除；
 *       只有 Rico 自己的下游服务 B 实例命中，流量闭环在 Rico 的本地链路中。</li>
 *   <li><b>跨集群同版本（降级）</b> — 当同集群无 {@code "rico-1.0.0"} 实例时，放宽集群限制，
 *       但仍要求 {@code current-version} 匹配。由于线上实例版本为 {@code "1.0.0"}，
 *       同样不匹配，因此不会误打到线上公共环境。</li>
 *   <li><b>跨集群跨版本（兜底）</b> — 仅当上述两级均无匹配时，按权重随机选择任意可用实例。
 *       此分支为兜底容错，正常运行下不应触达。</li>
 * </ol>
 * 由于各开发者的 {@code current-version} 带有个人标识且互不相同，第一级或第二级匹配即可精确命中
 * 开发者自己的本地实例，<b>有效避免请求被错误路由到其他开发者的本地实例或线上公共实例</b>。
 * <p>
 * <b>────────────────────────────────────────────────────────</b><br>
 * <b>依赖的 Nacos 元数据</b>
 * <b>────────────────────────────────────────────────────────</b>
 * <ul>
 *   <li>{@code current-version} — <b>（核心）</b>服务版本号，多人协作时携带开发者个人标识，用于流量隔离的主要匹配维度。</li>
 *   <li>{@code nacos.cluster} — 目标实例所属的 Nacos 集群名称，用于第一级匹配时的集群亲和性筛选。</li>
 *   <li>{@code nacos.weight} — 实例权重，由 {@link WeightedRandomSelector} 在同级候选实例中进行加权随机选择。</li>
 * </ul>
 * <p>
 * <b>使用方式：</b>在 Spring Cloud 负载均衡配置中注册本类为自定义 {@code ReactorServiceInstanceLoadBalancer} Bean 即可生效。
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 * @see ReactorServiceInstanceLoadBalancer
 * @see NacosDiscoveryProperties
 * @see WeightedRandomSelector
 */
@Slf4j
public class CanaryReleaseRule implements ReactorServiceInstanceLoadBalancer {

	private ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;
	/**
	 * 这个serviceId实际不需要, 只是为了记log
	 */
	private String serviceId;

	@Autowired
	private NacosDiscoveryProperties discoveryProperties;

	public CanaryReleaseRule(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
	                         String serviceId) {
		this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
		this.serviceId = serviceId;
	}

	@Override
	public Mono<Response<ServiceInstance>> choose(Request request) {

		ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider
				.getIfAvailable(NoopServiceInstanceListSupplier::new);
		return supplier.get(request).next().map(this::chooseInstance);
	}

	private Response<ServiceInstance> chooseInstance(List<ServiceInstance> instances) {
		//获取当前服务所在的集群名称
		String clusterName = discoveryProperties.getClusterName();
		//当前服务的版本号
		String version = discoveryProperties.getMetadata().get("current-version");

		List<ServiceInstance> theSameClusterNameAndTheSameVersionInstList = new ArrayList<>();
		// 自定义的选择算法，例如：返回第一个实例, 这里ServiceInstance的实例是 NacosServiceInstance
		for (ServiceInstance serviceInstance : instances) {
			if (StringUtils.equalsIgCase(clusterName, serviceInstance.getMetadata().get("nacos.cluster")) &&
				StringUtils.equalsIgCase(version, serviceInstance.getMetadata().get("current-version"))) {
				theSameClusterNameAndTheSameVersionInstList.add(serviceInstance);
			}
		}

		ServiceInstance targetInstance = null;
		//判断同集群同版本号的微服务实例是否为空
		if (theSameClusterNameAndTheSameVersionInstList.isEmpty()) {
			//跨集群调用相同的版本
			List<ServiceInstance> crossClusterAndTheSameVersionInstList = new ArrayList<>();
			for (ServiceInstance serviceInstance : instances) {
				if (StringUtils.equalsIgCase(version, serviceInstance.getMetadata().get("current-version"))) {
					crossClusterAndTheSameVersionInstList.add(serviceInstance);
				}
			}

			if (crossClusterAndTheSameVersionInstList.isEmpty()) {
				log.error("跨集群调用也找不到对应合适的版本, 当前版本为: {}", version);
				//throw new RuntimeException("找不到相同版本的微服务实例");
				log.info("跨集群跨版本调用");
				targetInstance = WeightedRandomSelector.chooseRandomlyByWeight(instances);
				return new DefaultResponse(targetInstance);
			} else {
				targetInstance = WeightedRandomSelector.chooseRandomlyByWeight(crossClusterAndTheSameVersionInstList);
				log.debug("跨集群同版本调用--->当前微服务所在集群:{},被调用微服务所在集群:{},当前微服务的版本:{},被调用微服务版本:{},Host:{},Port:{}",
						clusterName, targetInstance.getMetadata().get("nacos.cluster"), version,
						targetInstance.getMetadata().get("current-version"), targetInstance.getHost(), targetInstance.getPort());
			}

		}else {
			targetInstance = WeightedRandomSelector.chooseRandomlyByWeight(theSameClusterNameAndTheSameVersionInstList);
			log.debug("同集群同版本调用--->当前微服务所在集群:{},被调用微服务所在集群:{},当前微服务的版本:{},被调用微服务版本:{},Host:{},Port:{}",
					clusterName, targetInstance.getMetadata().get("nacos.cluster"), version,
					targetInstance.getMetadata().get("current-version"), targetInstance.getHost(), targetInstance.getPort());
		}

		return new DefaultResponse(targetInstance);
	}

}
