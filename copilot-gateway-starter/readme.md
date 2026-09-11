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

HTTP 状态码约定：
- `ResponseStatusException`（含静态资源 404 的 `NoResourceFoundException`）：**保留异常自带的状态码**（404 不再伪装成 200，否则浏览器会把 200 的 JSON 错误体当 JS 执行导致页面空白）
- `NotFoundException`（网关找不到路由/服务）：返回 404
- `GatewayException`（鉴权失败等业务异常）：返回 200 + 错误码 JSON，兼容既有前端

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

默认启用 CORS 支持，使用 Spring Cloud Gateway 原生的 `GlobalCorsProperties` 在 HandlerMapping 层面处理跨域，与 Gateway 路由机制完美集成。

```yaml
copilot:
  gateway:
    cors:
      enabled: true                        # 是否启用 CORS，默认 true
      allow-credentials: false             # 是否允许携带 Cookie
      max-age: 3600                        # 预检请求缓存时间（秒）
      allowed-origins:                     # 允许的跨域源
        - "*"
      allowed-headers:                     # 允许的请求头
        - "*"
      allowed-methods:                     # 允许的请求方法
        - "*"
```

> **注意：** 当 `allow-credentials: true` 时，`allowed-origins` 不能设置为 `*`，必须指定具体域名，否则浏览器会拦截响应。

## 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `copilot.gateway.auth.enabled` | boolean | false | 是否启用认证 |
| `copilot.gateway.auth.jwt-token` | boolean | false | 是否使用 JWT Token |
| `copilot.gateway.auth.client-id` | String | - | OAuth2 Client ID |
| `copilot.gateway.auth.client-secret` | String | - | OAuth2 Client Secret |
| `copilot.gateway.auth.auth-server-name` | String | - | 认证服务名 |
| `copilot.gateway.auth.should-skip-urls` | String | - | 跳过认证的 URL |
| `copilot.gateway.cors.enabled` | boolean | true | 是否启用 CORS 跨域 |
| `copilot.gateway.cors.allow-credentials` | boolean | false | 是否允许携带 Cookie |
| `copilot.gateway.cors.max-age` | Long | 3600 | 预检请求缓存时间（秒） |
| `copilot.gateway.cors.allowed-origins` | List | ["*"] | 允许的跨域源 |
| `copilot.gateway.cors.allowed-headers` | List | ["*"] | 允许的请求头 |
| `copilot.gateway.cors.allowed-methods` | List | ["*"] | 允许的请求方法 |

## 依赖说明

本 Starter 依赖以下模块：
- `copilot-gateway`：网关核心组件
- `spring-cloud-starter-gateway`：Spring Cloud Gateway
