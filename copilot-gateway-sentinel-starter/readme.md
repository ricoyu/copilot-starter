# Copilot Gateway Sentinel Starter

Spring Cloud Gateway 整合 Sentinel 限流 Starter。

## 功能特性

- ✅ Sentinel 限流集成
- ✅ 规则持久化支持（Nacos）
- ✅ 网关流控异常处理

## 快速开始

### 1. 引入 Maven 依赖

```xml
<dependency>
    <groupId>com.awesomecopilot</groupId>
    <artifactId>copilot-gateway-sentinel-starter</artifactId>
    <version>${copilot.version}</version>
</dependency>

<!-- Sentinel Gateway 适配器 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
</dependency>

<!-- Sentinel 规则持久化到 Nacos -->
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>
```

### 2. 配置 Sentinel

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8080
        port: 8719
      datasource:
        flow:
          nacos:
            server-addr: localhost:8848
            data-id: gateway-flow-rules
            group-id: SENTINEL_GROUP
            rule-type: gw-flow
```

## 功能说明

本 Starter 自动配置 Spring Cloud Gateway 与 Sentinel 的整合，提供：

1. **网关限流** - 基于 Sentinel 的网关流量控制
2. **规则持久化** - 支持将限流规则持久化到 Nacos
3. **异常处理** - 统一的限流异常响应

## 依赖说明

本 Starter 依赖以下模块：
- `spring-cloud-alibaba-sentinel-gateway`：Sentinel Gateway 适配器
- `sentinel-datasource-nacos`：Nacos 数据源（可选）
