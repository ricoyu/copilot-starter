# Copilot Gateway Starter

Spring Cloud Gateway 网关 Starter，提供网关常用功能自动配置。

## 功能特性

- ✅ 自定义路由谓词（TimeBetween）
- ✅ 统一异常处理
- ✅ JWT Token 认证
- ✅ OAuth2 认证
- ✅ CORS 跨域配置
- ✅ Sentinel 限流集成

## 快速开始

### 1. 引入 Maven 依赖

```xml
<dependency>
    <groupId>com.awesomecopilot</groupId>
    <artifactId>copilot-gateway-starter</artifactId>
    <version>${copilot.version}</version>
</dependency>
```

### 2. 基础配置

```yaml
copilot:
  gateway:
    auth:
      enabled: true                    # 是否启用认证
      jwt-token: true                  # 是否使用 JWT Token
      client-id: gateway_app           # OAuth2 Client ID
      client-secret: MTIzNDU2          # OAuth2 Client Secret
      auth-server-name: oauth-service  # 认证服务名
      should-skip-urls: /user/info     # 跳过认证的 URL
```

## 功能详解

### 1. TimeBetween 路由谓词

根据时间范围匹配路由：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: between-route
          uri: lb://order-service
          predicates:
            - TimeBetween=7:00, 17:38  # 仅在此时间段内路由
```

### 2. 统一异常处理

自动注册以下异常处理器：
- `GatewayBlockRequestHandler` - 限流异常处理
- `CopilotErrorWebExceptionHandler` - 网关层统一异常处理
- `GatewayExceptionHandlerAdvice` - 异常处理委托

### 3. JWT Token 认证

```yaml
copilot:
  gateway:
    auth:
      enabled: true
      jwt-token: true
```

启用后注册 `JwtAuthenticationFilter` 进行 JWT 认证。

### 4. OAuth2 认证

```yaml
copilot:
  gateway:
    auth:
      enabled: true
      jwt-token: false
      client-id: gateway_app
      client-secret: MTIzNDU2
      auth-server-name: oauth-service
```

启用后注册 `AuthenticationFilter` 和 `AuthorizationFilter`。

### 5. CORS 跨域配置

默认启用 CORS 支持，可通过 Spring Cloud Gateway 原生配置进行调整。

## 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `copilot.gateway.auth.enabled` | boolean | false | 是否启用认证 |
| `copilot.gateway.auth.jwt-token` | boolean | false | 是否使用 JWT Token |
| `copilot.gateway.auth.client-id` | String | - | OAuth2 Client ID |
| `copilot.gateway.auth.client-secret` | String | - | OAuth2 Client Secret |
| `copilot.gateway.auth.auth-server-name` | String | - | 认证服务名 |
| `copilot.gateway.auth.should-skip-urls` | String | - | 跳过认证的 URL |

## 依赖说明

本 Starter 依赖以下模块：
- `copilot-gateway`：网关核心组件
- `spring-cloud-starter-gateway`：Spring Cloud Gateway
