# Copilot Spring Cloud Starter

Spring Cloud 微服务 Starter，提供微服务开发常用功能自动配置。

## 功能特性

- ✅ 接口幂等性（@Idempotent）
- ✅ Sentinel 异常处理
- ✅ Sentinel 授权规则
- ✅ 多租户支持（Feign 传递 Tenant-Id）
- ✅ Feign 拦截器自动配置
- ✅ 金丝雀发布负载均衡（开发者流量隔离）

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
```

## 功能详解

### 1. 接口幂等性 @Idempotent

**被调用方配置：**

```yaml
copilot:
  idempotent:
    enabled: true
```

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

### 5. 金丝雀发布负载均衡

启用后，自定义 `CanaryReleaseRule` 将替代 Spring Cloud 默认的负载均衡策略，基于 Nacos 元数据中的 `current-version` 实现**开发者级别的流量隔离**，确保多人协作时请求不会被错误路由到其他开发者的本地实例。

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

## 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `copilot.idempotent.enabled` | boolean | false | 是否启用幂等性 |
| `copilot.sentinel.rest-exception-enabled` | boolean | true | Sentinel 异常处理 |
| `copilot.sentinel.auth-rule.enabled` | boolean | false | 授权规则 |
| `copilot.sentinel.auth-rule.header` | String | Auth-Origin | 来源请求头名称 |
| `copilot.filter.tenant.mandatory` | boolean | false | 是否强制要求租户ID |
| `copilot.lb.canary-release.enabled` | boolean | false | 是否启用金丝雀发布负载均衡 |

## 依赖说明

本 Starter 依赖以下模块：
- `copilot-spring-cloud`：Spring Cloud 核心组件
- `spring-cloud-starter-openfeign`：OpenFeign
- `copilot-cache`：Redis 缓存支持
