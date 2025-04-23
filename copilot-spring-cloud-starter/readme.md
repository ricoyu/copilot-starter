# 一 接口幂等性@Idempotent

* 在需要做接口幂等性的接口方法上添加@Idempotent注解
* 调用方的feign要加上这个拦截器 com.awesomecopilot.cloud.feign.interceptor.IdempotentInterceptor 以填充Idempotent请求头到Header中
* 处理@Idempotent注解的是IdempotentAspect
* 加了@Idempotent注解的微服务要在application.yaml中配置 copilot.idemtotent.enabled: true, 否则不会生效, 这个配置项的作用是配置bean: IdempotentAspect

目前基于Redis的Hyperloglog来实现, 开启开关:

```properties
loser.idemtotent.enabled=true
```

在要做幂等性控制的方法上加@Idemtotent 注解, 如果被幂等性控制了, 返回调用成功, 但是返回的数据是null

1. 被调用方在resources下要放置redis.properties, 配置JedisUtils连接信息

   连接单实例Redis配置示例:

   ```properties
   redis.host=localhost
   redis.port=6379
   redis.password=123456
   ```

   连接Sentinel配置示例:

   ```properties
   redis.sentinels=192.168.100.101:26379,192.168.100.102:26379,192.168.100.103:26379
   redis.password=123456
   ```

   连接Redis集群配置示例

   ```properties
   redis.clusters=192.168.100.101:6379,192.168.100.101:6380,192.168.2.102:6379,192.168.2.102:6380,192.168.2.103:6379,192.168.2.103:6380
   redis.password=123456
   ```



# 二 Sentinel异常处理

开启开关, 这是默认就开启的, 要关闭设为false即可

```yaml
copilot:
  sentinel:
    enabled: true
```

Sentinel注册了一个AbstractSentinelInterceptor, 这是实现了Spring MVC的HandlerInterceptor, 在其preHandle方法里面捕获流控异常, 然后交给DefaultBlockExceptionHandler去处理, 这个类就默认返回字符串 "Blocked by Sentinel (flow limiting)", 我这边提供了一个返回REST结果的RestBlockExceptionHandler



# 三 Sentinel 授权规则流控

提供了RequestHeaderInterceptor和MyRequestOriginParser

需要分别在调用方配置Bean: RequestHeaderInterceptor

被调用方配置Bean: MyRequestOriginParser

application.yaml配置项

```yaml
copilot.sentinel.rest-exception-enabled: true
```

控制是否要对流控异常做统一异常处理



# 四 租户ID

CopilotSpringCloudAutoConfiguration里面配置了全局的TenantIdInterceptor, 不需要手工配置, 对所有Feign客户端生效, 从当前请求头中拿Tenant-Id, 如果有的话塞到Feign的请求头中传递下去, 因为这个只是检查一下有没有, 有的话传递下去, 所以不需要开关, 默认开启就是了

```java
/**
 * 用于在feign调用的时候传递请求头中的租户ID
 * @return
 */
@Bean
@ConditionalOnMissingBean(TenantIdInterceptor.class)
public TenantIdInterceptor tenantIdInterceptor() {
  return new TenantIdInterceptor();
}
```

配合

```yaml
copilot:
  filter:
    tenant:
      mandatory: true
```

达到强制传TenantId的效果
