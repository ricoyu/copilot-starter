# Copilot Spring Cloud Starter

Spring Cloud 微服务 Starter，提供微服务开发常用功能自动配置。

## 功能特性

- ✅ 接口幂等性（@Idempotent）
- ✅ Sentinel 异常处理
- ✅ Sentinel 授权规则
- ✅ 多租户支持（Feign 传递 Tenant-Id）
- ✅ Feign 自动透传 Authorization 请求头
- ✅ 过滤器链未处理异常的统一 500 JSON 响应（ExceptionFilter）
- ✅ 金丝雀发布负载均衡（开发者流量隔离），版本号元数据键名可配（`copilot.discovery.metadata.version-key`）

条件说明：`CopilotSpringCloudAutoConfiguration` 整体要求 Servlet 应用（`@ConditionalOnWebApplication(SERVLET)`）。

## 快速开始

### 1. 引入 Maven 依赖

```xml
<dependency>
    <groupId>com.awesomecopilot</groupId>
    <artifactId>copilot-spring-cloud-starter</artifactId>
    <version>${copilot.version}</version>
</dependency>
```

### 2. 基础配置

```yaml
copilot:
  # 幂等性配置
  idempotent:
    enabled: true
  
  # Sentinel 配置
  sentinel:
    rest-exception-enabled: true       # Sentinel 异常处理，默认 true
    auth-rule:
      enabled: false                   # 授权规则，默认 false
      header: Auth-Origin              # 来源请求头
  
  # 多租户配置
  filter:
    tenant:
      mandatory: false                 # 是否强制要求 Tenant-Id
  
  # 金丝雀发布负载均衡
  lb:
    canary-release:
      enabled: false                   # 是否启用金丝雀发布，默认 false

  # 金丝雀匹配依赖的 Nacos 元数据键名
  discovery:
    metadata:
      version-key: current-version     # 版本号元数据键名，默认 current-version
```

## 功能详解

### 1. 接口幂等性 @Idempotent

**调用方（Feign 客户端侧）** 开关是 `copilot.idempotent.enabled`（默认关），打开后 `IdempotentInterceptor` 给所有 Feign 请求加 `Idempotent: <UUID>` 请求头（超时重试携带同一值）。

**被调用方（服务端切面侧）** 开关的键是 `copilot.idemtotent.enabled`——注意拼写：源码里就是这个 `idemtotent`（IdempotentAspect 的 `@ConditionalOnProperty` 原样如此），写成正确的 `idempotent` 反而切面不装配。属性类 `IdemtotentProperties` 的前缀则是 `copilot.idempotent`，两个键管的东西不同，都要按需配置。

在需要幂等性控制的方法上添加注解：

```java
@Idempotent
@PostMapping("/order")
public Result createOrder(@RequestBody OrderDTO dto) {
    // 业务逻辑
}
```

**调用方配置：**

Feign 客户端添加拦截器：

```java
@FeignClient(name = "order-service")
public interface OrderFeignClient {
    // 自动填充 Idempotent-Token 请求头
}
```

**Redis 配置（src/main/resources/redis.properties）：**

```properties
# 单实例
redis.host=localhost
redis.port=6379
redis.password=123456

# Sentinel 模式
redis.sentinels=192.168.100.101:26379,192.168.100.102:26379
redis.password=123456

# 集群模式
redis.clusters=192.168.100.101:6379,192.168.100.101:6380
redis.password=123456
```

### 2. Sentinel 异常处理

```yaml
copilot:
  sentinel:
    rest-exception-enabled: true  # 默认 true
```

自动注册 `RestBlockExceptionHandler`，返回 REST 格式异常响应：

```json
{"code":"42901","desc":"已被流控"}
```

### 3. Sentinel 授权规则

```yaml
copilot:
  sentinel:
    auth-rule:
      enabled: true
      header: Auth-Origin  # 默认值
```

自动配置：
- `CopilotOriginParser` - 解析请求来源
- `AuthFlowInterceptor` - Feign 调用时传递 Origin 请求头

### 4. 多租户支持

自动配置 `TenantIdInterceptor`，在所有 Feign 调用中传递 `Tenant-Id` 请求头。

配合 Web Starter 的强制校验：

```yaml
copilot:
  filter:
    tenant:
      mandatory: true  # 强制要求携带 Tenant-Id
```

### 5. Authorization 请求头补写与异常统一响应

- `AuthorizationInterceptor`（无条件注册）：Feign 请求模板没有 `Authorization` 头时，补一个 `Authorization: <随机UUID>`。设计初衷是把上游请求的认证头带下去，但当前实现并不读取原请求的 Authorization 值，而是塞 UUID——依赖下游按 token 校验的场景请勿开启使用，该行为已在评审报告中列为待修项。
- `ExceptionFilter`（无条件注册，`/*`）：catch 过滤器链上的未处理异常，统一回 500 的 `Result` JSON，避免 Servlet 容器默认错误页。

### 6. 金丝雀发布负载均衡

启用后，自定义 `CanaryReleaseRule` 将替代 Spring Cloud 默认的负载均衡策略，基于 Nacos 元数据中的版本号（键名默认 `current-version`，可通过 `copilot.discovery.metadata.version-key` 覆盖）实现**开发者级别的流量隔离**，确保多人协作时请求不会被错误路由到其他开发者的本地实例。

**启用配置：**

```yaml
copilot:
  lb:
    canary-release:
      enabled: true
```

**开发者本地 Nacos 元数据配置（关键）：**

每位开发者需在本地 `application-dev.yml` 中配置**携带个人标识的版本号**：

```yaml
# 开发者 Rico
spring:
  cloud:
    nacos:
      discovery:
        metadata:
          current-version: rico-1.0.0

# 开发者 Paul
spring:
  cloud:
    nacos:
      discovery:
        metadata:
          current-version: paul-1.0.0

# 线上公共环境
spring:
  cloud:
    nacos:
      discovery:
        metadata:
          current-version: 1.0.0
```

**隔离原理 — 三级匹配策略：**

| 优先级 | 策略 | 匹配条件 | 说明 |
|--------|------|----------|------|
| 1 | 同集群同版本 | `nacos.cluster` 相同 + `current-version` 相同 | 最优，流量闭环在开发者本地 |
| 2 | 跨集群同版本 | `current-version` 相同 | 降级，本地无同版本实例时跨集群匹配 |
| 3 | 跨集群跨版本 | 无限制，按权重随机 | 兜底容错，正常运行不应触达 |

由于各开发者的 `current-version` 带有个人标识且互不相同，第一级或第二级匹配即可精确命中开发者自己的本地实例，有效避免请求串扰。

> 表中的 `current-version` 指默认键名，实际参与匹配的版本号键名以 `copilot.discovery.metadata.version-key` 为准。

**版本号元数据键名可配置（`copilot.discovery.metadata`）：**

版本号在元数据中的键名不写死在代码里，而是收敛到 `copilot.discovery.metadata` 下，默认值即为上面示例使用的 `current-version`，**不配置时行为与历史完全一致**：

```yaml
copilot:
  discovery:
    metadata:
      version-key: current-version   # 默认值；版本号元数据键名
```

仅当团队在 Nacos 元数据里使用的是别的键名（例如 `gray-version`）时才需要覆盖；覆盖后 `spring.cloud.nacos.discovery.metadata` 中的实际键名必须与之一致，否则一、二级匹配为空并退化到第三级全量随机。

集群名与权重（`nacos.cluster` / `nacos.weight`）**不作为配置项**：它们由 Spring Cloud Alibaba 标准配置 `spring.cloud.nacos.discovery.cluster-name` 等决定，上报到 Nacos 后的元数据键名固定。

**装配细节：**

- 属性类 `com.awesomecopilot.cloud.properties.DiscoveryMetadataProperties`（前缀 `copilot.discovery.metadata`，位于 `copilot-spring-cloud` 模块）由 `DefaultLBConfig` 的 `@EnableConfigurationProperties` 注册，只在 `copilot.lb.canary-release.enabled=true` 时生效。
- `CanaryReleaseLoadBalancerConfiguration` 通过 `ObjectProvider<DiscoveryMetadataProperties>` 把它传给 `CanaryReleaseRule` 构造方法；如果业务方绕过 `DefaultLBConfig` 自行注册该负载均衡配置类（属性 Bean 未装配），会自动退化为默认键名 `current-version`，不会因缺 Bean 而启动失败。
- 自定义负载均衡器跑在 LoadBalancer 子上下文中，属性 Bean 注册在主上下文，靠父子上下文解析，因此该配置项**只需在主配置文件（如 `application.yml`）里写一份**即可。

## 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `copilot.idempotent.enabled` | boolean | false（matchIfMissing=false） | Feign 侧幂等请求头拦截器 |
| `copilot.idemtotent.enabled` | boolean | false | 服务端 @Idempotent 切面（键名拼写同源码） |
| `copilot.sentinel.enabled` | boolean | true（matchIfMissing=true） | SentinelResourceAspect 装配开关（属性类字段默认 false，但不配置时条件视为通过） |
| `copilot.sentinel.rest-exception-enabled` | boolean | true | Sentinel 异常处理 |
| `copilot.sentinel.auth-rule.enabled` | boolean | true（matchIfMissing=true） | 授权规则（不配置即装配 CopilotOriginParser + AuthFlowInterceptor；想关必须显式配 false——与旧文档"默认 false"不符，以源码条件为准） |
| `copilot.sentinel.auth-rule.header` | String | Auth-Origin | 来源请求头名称 |
| `copilot.filter.tenant.mandatory` | boolean | false | 是否强制要求租户ID（消费方在 web 模块 TenantIdFilter） |
| `copilot.lb.canary-release.enabled` | boolean | false | 是否启用金丝雀发布负载均衡 |
| `copilot.discovery.metadata.version-key` | String | current-version | 金丝雀匹配使用的版本号元数据键名（属性类 `DiscoveryMetadataProperties`） |

## 依赖说明

本 Starter 依赖以下模块：
- `copilot-spring-cloud`：Spring Cloud 核心组件
- `spring-cloud-starter-openfeign`：OpenFeign
- `copilot-cache`：Redis 缓存支持
