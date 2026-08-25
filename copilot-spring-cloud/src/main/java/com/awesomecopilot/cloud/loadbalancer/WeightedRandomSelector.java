package com.awesomecopilot.cloud.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;

import java.util.List;
import java.util.Random;

/**
 * 基于Nacos权重随机选择一个
 * <p/>
 * Copyright: Copyright (c) 2024-11-01 20:14
 * <p/>
 * Company: Sexy Uncle Inc.
 * <p/>

 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class WeightedRandomSelector {

	public static ServiceInstance chooseRandomlyByWeight(List<ServiceInstance> instances) {
		if (instances == null || instances.isEmpty()) {
			return null;
		}

		double totalWeight = 0.0;
		for (ServiceInstance instance : instances) {
			double weight = Double.parseDouble(instance.getMetadata().get("nacos.weight"));
			totalWeight +=weight;
		}

		// 生成一个随机数，并确定选中的区间
		double randomValue = new Random().nextDouble() * totalWeight;
		double sum = 0.0;
		for (ServiceInstance instance : instances) {
			sum += Double.parseDouble(instance.getMetadata().get("nacos.weight"));
			if (randomValue <= sum) {
				return instance;
			}
		}

		// 理论上不会执行到这里，除非计算过程中有误差
		return null;
	}
}