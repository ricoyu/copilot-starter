# Copilot Spring Cloud Starter

Spring Cloud 微服务 Starter，提供微服务开发常用功能自动配置。

## 功能特性

- ✅ 接口幂等性（@Idempotent）
- ✅ Sentinel 异常处理
- ✅ Sentinel 授权规则
- ✅ 多租户支持（Feign 传递 Tenant-Id）
- ✅ Feign 拦截器自动配置

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

## 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `copilot.idempotent.enabled` | boolean | false | 是否启用幂等性 |
| `copilot.sentinel.rest-exception-enabled` | boolean | true | Sentinel 异常处理 |
| `copilot.sentinel.auth-rule.enabled` | boolean | false | 授权规则 |
| `copilot.sentinel.auth-rule.header` | String | Auth-Origin | 来源请求头名称 |
| `copilot.filter.tenant.mandatory` | boolean | false | 是否强制要求租户ID |

## 依赖说明

本 Starter 依赖以下模块：
- `copilot-spring-cloud`：Spring Cloud 核心组件
- `spring-cloud-starter-openfeign`：OpenFeign
- `copilot-cache`：Redis 缓存支持
