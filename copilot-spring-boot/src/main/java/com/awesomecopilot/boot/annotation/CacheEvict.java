package com.awesomecopilot.boot.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 清除缓存
 * <p>
 * 采用延时双删策略保证缓存与数据库的双写一致性 <br/>
 * 在执行目标方法前先删一遍缓存 <br/>
 * 目标方法执行成功后延迟1秒再删一遍
 * <p>
 * <ul>演示双删策略解释
 *     <li/>先删缓存是为了防止写数据库已经完成, 但是休眠1秒删缓存还没有执行的情况下, 用户会请求到老的缓存数据, 所以要先删缓存
 *     <li/>休眠1秒再删是因为先删除了缓存, 然后写数据库, 有可能事务还没提交, 又有新的查询进来, 就会读到数据库的老数据, 然后更新到缓存, 导致缓存的数据也是老数据
 *     <li/>这么做, 可以将1秒内所造成的缓存脏数据再次删除
 * </ul>
 * 如果缓存删除失败, 不会往上抛异常, 避免缓存删除失败导致业务主流程失败
 * <p/>
 * Copyright: Copyright (c) 2025-12-13 16:07
 * <p/>
 * Company: Sexy Uncle Inc.
 * <p/>

 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheEvict {

	/**
	 * 要清除的缓存key
	 */
	String[] keys();
	
	/**
	 * 目标方法成功返回后, 延迟多少秒执行第二遍删除.
	 * 默认-1表示使用全局配置 copilot.cache.evict-delay-seconds;
	 * 填 0 或负数同样按全局默认处理;
	 * 主从复制延迟高的环境可以按方法单独调大这个值.
	 */
	long evictDelaySeconds() default -1;
}
