# Copilot Gateway Starter

Spring Cloud Gateway 网关 Starter，提供网关常用功能自动配置。

## 功能特性

- ✅ 自定义路由谓词（TimeBetween）
- ✅ 路由级耗时记录过滤器（TimeMonitor）
- ✅ 统一异常处理
- ✅ Bearer Token 认证（token 存 Redis，由认证服务签发）
- ✅ CORS 跨域配置（默认关闭，需显式开启）
- ✅ `@LoadBalanced` 的 CopilotRestTemplate（服务启动早期即可按服务名调用）

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
      enabled: true                    # 认证总开关，默认 false
      white-list:                      # 免认证路径(Ant 匹配)，默认 []
        - /user/info
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

自动注册以下异常处理器（`CopilotExceptionAutoConfiguration`，REACTIVE 应用条件）：
- `CopilotErrorWebExceptionHandler` - 网关层统一异常处理
- `GatewayExceptionHandlerAdvice` - 异常处理委托

限流异常的 `GatewayBlockRequestHandler` 不在本 starter——它由 `copilot-gateway-sentinel-starter` 在 `copilot.gateway.sentinel.enabled=true` 时注册。

HTTP 状态码约定：
- `ResponseStatusException`（含静态资源 404 的 `NoResourceFoundException`）：**保留异常自带的状态码**（404 不再伪装成 200，否则浏览器会把 200 的 JSON 错误体当 JS 执行导致页面空白）
- `NotFoundException`（网关找不到路由/服务）：返回 404
- `GatewayException`（鉴权失败等业务异常）：返回 200 + 错误码 JSON，兼容既有前端

### 3. Token 认证（AuthenticationFilter）

`copilot.gateway.auth.enabled=true` 时注册 `AuthenticationFilter`（GlobalFilter）：

1. 请求路径命中 `copilot.gateway.auth.white-list`（Ant 风格）则直接放行；
2. 否则要求 `Authorization: Bearer <token>`，去掉前缀后调 `AuthUtils.checkToken` 到 **Redis** 校验（token 由登录侧/security6 模块签发进 Redis），无效或过期返回未认证错误。
3. **重点:** 网关只做认证, 授权下沉到各微服务去做

说明：本 starter 没有 JWT 验签实现，也没有 OAuth2 client-credentials 组件；`jwt-token`、`client-id`、`client-secret`、`auth-server-name` 这些键在 `CopilotGatewayProperties.Auth` 里都不存在，配了也不会绑定生效。属性类里另有一个 `token-key-endpoint`（默认 `/oauth/token_key`），当前源码未见消费点。

### 4. TimeMonitor 耗时过滤器

按路由/全局 default-filters 使用，记录请求进入网关到响应完成耗时并打日志：

```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - name: TimeMonitor
          args:
            name: total-time
```

### 5. CopilotRestTemplate

`CopilotGatewayAutoConfiguration` 注册了 `@LoadBalanced` 的 `CopilotRestTemplate`（基于服务发现）：注册表还没同步完成的启动早期也能按服务名发起调用（普通 RestTemplate 此时会 unknown host）。

### 6. CORS 跨域配置

**默认不启用**：`CorsWebFilter` 的条件是 `copilot.gateway.cors.enabled` 显式配 `true`（`matchIfMissing=false`）——属性类里 CORS.enabled 字段虽是 true，但没有任何配置时 `@ConditionalOnProperty` 直接跳过注册。启用后走全局 `CorsWebFilter`（`HIGHEST_PRECEDENCE`）。

```yaml
copilot:
  gateway:
    cors:
      enabled: true                        # 必须显式配 true(不配则不注册 CorsWebFilter)
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

### 7. Sentinel 网关限流

本 starter 的 `CopilotGatewayAutoConfiguration` 之外另有一个 `CopilotSentinelGatewayAutoConfig`（类上有 `@ConditionalOnClass(SentinelGatewayFilter.class)`），但它**没有列进 `AutoConfiguration.imports`、也没有任何 `@Import` 引用**——当前版本不会自动装配 SentinelGatewayFilter，"网关 Sentinel 整合"请走 `copilot-gateway-sentinel-starter`。

## 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `copilot.gateway.auth.enabled` | boolean | false | 是否启用 Token 认证 |
| `copilot.gateway.auth.white-list` | List | [] | 免认证路径（Ant 风格） |
| `copilot.gateway.auth.token-key-endpoint` | String | /oauth/token_key | 属性类里存在，当前源码未见消费点 |
| `copilot.gateway.time-monitor-filter-enabled` | boolean | true（不配置即启用） | TimeMonitor 路由过滤器开关 |
| `copilot.gateway.cors.enabled` | boolean | -（需显式配 true） | 未配置时 CorsWebFilter 不注册 |
| `copilot.gateway.cors.allow-credentials` | boolean | false | 是否允许携带 Cookie |
| `copilot.gateway.cors.max-age` | Long | 3600 | 预检请求缓存时间（秒） |
| `copilot.gateway.cors.allowed-origins` | List | ["*"] | 允许的跨域源 |
| `copilot.gateway.cors.allowed-headers` | List | ["*"] | 允许的请求头 |
| `copilot.gateway.cors.allowed-methods` | List | ["*"] | 允许的请求方法 |

## 依赖说明

本 Starter 依赖以下模块：
- `copilot-gateway`：网关核心组件
- `spring-cloud-starter-gateway`：Spring Cloud Gateway
