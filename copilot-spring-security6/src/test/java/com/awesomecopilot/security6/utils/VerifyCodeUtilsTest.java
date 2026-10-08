package com.awesomecopilot.security6.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证码文本生成测试（评审报告 P1-3）.
 * <p>
 * 旧实现两个缺陷:
 * ① new Random(System.currentTimeMillis()) —— 攻击者拿到码图+大致生成时刻, 可用同毫秒种子枚举出文本;
 * ② nextInt(codesLen - 1) —— 字符表最后一个字符 'Z' 永远取不到, 40 个字符实际只有 39 个可用.
 *
 * @author Rico Yu  ricoyu520@gmail.com
 */
class VerifyCodeUtilsTest {
	
	@Test
	void everyCharacterInAlphabetIsReachable() {
		// 旧实现的取样缺陷有两个叠加: ①nextInt(codesLen-1) 让最后一个字符 'Z' 的取样概率恒为 0;
		// ②毫秒种子让 50 次调用只产生"经过的毫秒数"这么多种不同序列, 样本覆盖远小于 40 字符.
		// 修复后 2000 个字符里单字符缺席概率约 (39/40)^2000 ≈ 5e-23, 不会闪断.
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 50; i++) {
			sb.append(VerifyCodeUtils.generateVerifyCode(40));
		}
		String all = sb.toString();
		for (char c : VerifyCodeUtils.VERIFY_CODES.toCharArray()) {
			assertThat(all.indexOf(c))
				.as("字符 '%s' 在 2000 次取样中一次都没出现", c)
				.isGreaterThanOrEqualTo(0);
		}
	}
	
	@Test
	void consecutiveCodesAreNotSeedPredictable() {
		// 旧实现: 同一毫秒内所有调用共享同一个种子, 生成的码完全相同.
		// 2000 次连续调用只需几毫秒到几十毫秒, 旧实现不同值只有"经过了多少毫秒"这么多种(约 10~50);
		// 修复后 8 位码在 40^8≈6.6e12 空间里取, 2000 次里两两相撞的期望次数不到百万分之一.
		Set<String> codes = new HashSet<>();
		for (int i = 0; i < 2000; i++) {
			codes.add(VerifyCodeUtils.generateVerifyCode(8));
		}
		assertThat(codes.size())
			.as("同一时间窗内生成的验证码几乎全部相同 => 仍是毫秒种子 Random")
			.isGreaterThan(1900);
	}
}
