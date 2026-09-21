package com.awesomecopilot.boot.autoconfig.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ObjectMapperDecorationTracker 的并发行为测试（评审报告 P2-7）.
 * <p>
 * markDecorated 是"判重+登记"合一的原子入口: 同一个 mapper 并发打入, 只允许一个线程拿到 true
 * (即只装饰一次); 修复前 newSetFromMap(IdentityHashMap) 无并发保护, add 的读-改-写可被交错,
 * 同一 mapper 会被多个线程同时判为"未装饰".
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class ObjectMapperDecorationTrackerTest {
	
	@Test
	void concurrentMarkDecoratedOnSameMapperAllowsExactlyOneWinner() throws Exception {
		ObjectMapperDecorationTracker.reset();  //隔离其他用例的登记状态
		//多轮循环: 竞态是概率性的(评审实测旧实现单轮约1.5%概率出多赢家, 跑400轮中6轮),
		//只跑一轮抓不到回归; 200轮总耗时约数秒
		for (int round = 0; round < 200; round++) {
			assertOneWinnerPerRound();
		}
	}
	
	private void assertOneWinnerPerRound() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		
		int threads = 16;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger winners = new AtomicInteger();
		Set<Boolean> observed = ConcurrentHashMap.newKeySet();
		
		try {
			for (int i = 0; i < threads; i++) {
				pool.submit(() -> {
					try {
						start.await();
						if (ObjectMapperDecorationTracker.markDecorated(mapper)) {
							winners.incrementAndGet();
						}
						observed.add(ObjectMapperDecorationTracker.isDecorated(mapper));
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				});
			}
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		} finally {
			ObjectMapperDecorationTracker.reset();
		}
		
		assertThat(winners.get()).isEqualTo(1);
		assertThat(observed).containsExactly(true);
	}
	
	@Test
	void distinctMappersAreTrackedSeparately() {
		ObjectMapperDecorationTracker.reset();
		try {
			ObjectMapper a = new ObjectMapper();
			ObjectMapper b = new ObjectMapper();
			//IdentityHashMap 语义: 两个内容相同但不同实例的 mapper 各自登记
			assertThat(ObjectMapperDecorationTracker.markDecorated(a)).isTrue();
			assertThat(ObjectMapperDecorationTracker.markDecorated(b)).isTrue();
			assertThat(ObjectMapperDecorationTracker.markDecorated(a)).isFalse();
		} finally {
			ObjectMapperDecorationTracker.reset();
		}
	}
}
