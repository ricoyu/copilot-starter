# Copilot Gateway Sentinel Starter

Spring Cloud Gateway 整合 Sentinel 限流 Starter。

## 功能特性

- ✅ 网关流控被拦截时的统一 JSON 错误响应（GatewayBlockRequestHandler）
- ✅ 规则持久化支持（Nacos，需应用自行引依赖并配置）

## 本 Starter 实际装配的东西

只有一处自动配置 `CopilotGatewaySentinelAutoConfig`：当 `copilot.gateway.sentinel.enabled=true` 时注册
`GatewayBlockRequestHandler`（继承 Sentinel 的 DefaultBlockRequestHandler，替换默认的"网关被限流/降级"错误页）。
**它不注册 SentinelGatewayFilter 本身**——过滤器由 `spring-cloud-alibaba-sentinel-gateway`
的官方自动配置装配（引入该依赖即生效），本 starter 只负责被拦截后的响应形态。

## 快速开始

### 1. 引入 Maven 依赖

```xml
<dependency>
    <groupId>com.awesomecopilot</groupId>
    <artifactId>copilot-gateway-sentinel-starter</artifactId>
    <version>${copilot.version}</version>
</dependency>
```

`spring-cloud-alibaba-sentinel-gateway`、`sentinel-spring-cloud-gateway-adapter`、
`spring-cloud-starter-alibaba-sentinel` 已由本 starter 的 pom 传递引入，无需手加。
规则持久化到 Nacos 才需要自行添加：

```xml
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>
```

### 2. 启用与配置 Sentinel

```yaml
copilot:
  gateway:
    sentinel:
      enabled: true          # 不配置=false, GatewayBlockRequestHandler 不注册
spring:
  cloud:
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

## 被限流时的响应

`GatewayBlockRequestHandler`（继承 `DefaultBlockRequestHandler`）：
- 普通请求：HTTP 200 + JSON 错误体（含限流错误码），保持与 `copilot-spring-boot-web` 的 Result 约定一致，前端按业务错误处理；
- 浏览器导航类请求（Accept: text/html）：回 HTML 错误响应。

## 依赖说明

本 Starter 依赖：
- `copilot-gateway`（读取 `copilot.gateway.*` 属性）
- `spring-cloud-starter-alibaba-sentinel` / `spring-cloud-alibaba-sentinel-gateway` / `sentinel-spring-cloud-gateway-adapter`（pom 直接依赖，传递生效）
