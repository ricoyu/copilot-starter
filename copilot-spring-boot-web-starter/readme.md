# Copilot Spring Boot Web Starter

Spring Boot Web 应用 Starter，提供 Web 开发常用功能自动配置。

## 功能特性

- ✅ 日期类型绑定支持（Date、LocalDate、LocalDateTime、LocalTime）
- ✅ Enum 类型参数绑定（大小写不敏感）
- ✅ WebSocket 分布式推送支持
- ✅ 国际化支持（i18n）
- ✅ Jackson 定制增强
- ✅ UTF-8 编码自动配置
- ✅ ThreadLocal 自动清理
- ✅ 接口限流（@RateLimit）
- ✅ 接口幂等性（@Idempotent）
- ✅ 全局异常处理
- ✅ RequestBody 可重复读取
- ✅ 多租户支持
- ✅ 接口签名验证
- ✅ 分页查询支持
- ✅ Tomcat 线程池监控
- ✅ CORS 跨域配置
- ✅ XSS 防护
- ✅ Redis 缓存双写一致性（@CacheEvict）

## 快速开始

### 1. 引入 Maven 依赖

```xml
<dependency>
    <groupId>com.awesomecopilot</groupId>
    <artifactId>copilot-spring-boot-web-starter</artifactId>
    <version>${copilot.version}</version>
</dependency>
```

### 2. 基础配置

```yaml
copilot:
  # MVC 配置
  mvc:
    cors-enabled: false                    # CORS 跨域，默认 false
    rest-exception-advice-enabled: true    # 全局异常处理，默认 true
    api-sign:
      enabled: false                       # 接口签名，默认 false
  
  # Filter 配置
  filter:
    repeated-read: false                   # RequestBody 可重复读取，默认 false
    pool-statistic: false                  # Tomcat 线程池监控，默认 false
    xss-enabled: false                     # XSS 防护，默认 false
    tenant:
      mandatory: false                     # 是否强制要求 Tenant-Id 请求头，默认 false
  
  # WebSocket 配置
  websocket:
    enabled: false                         # 是否启用 WebSocket，默认 false
    path-prefix: /ws/push/**               # WebSocket 路径前缀
  
  # 国际化配置
  locale:
    enabled: false                         # 是否启用国际化，默认 false
  
  # 幂等性配置
  idempotent:
    enabled: true                          # 是否启用幂等性，默认 true
  
  # 缓存配置
  cache:
    enabled: false                         # 是否启用缓存，默认 false
```

## 功能详解

### 1. 日期类型绑定

自动支持以下日期类型的 URL 参数绑定：
- `java.util.Date`
- `java.time.LocalDate`
- `java.time.LocalDateTime`
- `java.time.LocalTime`

```java
@GetMapping("/birthday")
public Date dateBind(Date birthday) {
    return birthday;
}
// 请求: http://localhost:8080/birthday?birthday=2982-11-09
```

### 2. Enum 类型参数绑定

支持按 enum 的 `code` 或 `desc` 属性绑定，大小写不敏感：

```java
public enum OrderType {
    SEC_KILL(100, "秒杀"),
    PROMOTION(99, "促销");
    
    private int code;
    private String desc;
}

@GetMapping("/type")
public OrderType getType(OrderType orderType) {
    return orderType;
}
```

支持的请求：
- `http://localhost:8080/type?orderType=促销`
- `http://localhost:8080/type?orderType=99`
- `http://localhost:8080/type?orderType=sec_kill`

### 3. WebSocket 分布式推送

```yaml
copilot:
  websocket:
    enabled: true
    path-prefix: /ws/push/**
  cache:
    enabled: true
```

通过 HTTP 接口触发推送：
```
POST http://localhost:8080/ws/push/weekend
Body: {"message": "周末愉快"}
```

系统会自动通过 Redis PUB/SUB 通知所有分布式节点推送消息。

### 4. 国际化支持

```yaml
copilot:
  locale:
    enabled: true
```

在 `src/main/resources/i18n/` 下创建：
- `messages.properties` - 中文
- `messages_en_US.properties` - 英文
- `messages_zh_CN.properties` - 中文（Linux 必需）

编程方式获取：
```java
String message = I18N.i18nMessage("account.retry.locked", 3, 1000);
```

### 5. 接口幂等性

```java
@Idempotent
@PostMapping("/submit")
public Result submit() {
    // 业务逻辑
}
```

客户端先请求 `/idempotent-token` 获取 token，然后设置请求头：
```
Idempotent-Token: <token>
```

### 6. 多租户支持

```yaml
copilot:
  filter:
    tenant:
      mandatory: true  # 强制要求 Tenant-Id 请求头
```

客户端请求需携带 `Tenant-Id` 请求头。

### 7. 接口签名验证

```yaml
copilot:
  mvc:
    api-sign:
      enabled: true
```

客户端签名流程：
1. 获取时间戳 `timestamp`
2. 生成随机串 `nonce`
3. 拼接 `message = uri=${uri}&timestamp=${timestamp}&nonce=${nonce}`
4. SHA256 哈希生成签名
5. 设置请求头：`Timestamp`、`Nonce`、`Signature`

### 8. 分页查询

**方式1：DTO 继承 PageDTO**

```java
@Data
public class UserQueryDTO extends PageDTO {
    private String name;
    private Integer status;
}

@PostMapping("/list")
public Result<List<User>> list(@RequestBody UserQueryDTO dto) {
    List<User> users = userService.queryPage(dto);
    return Results.<List<User>>success().data(users).build();
}
```

**方式2：DTO 包含 Page 属性**

```java
@Data
public class UserQueryDTO {
    private String name;
    private Page page;
}
```

返回结果自动包含分页信息：
```json
{
    "code": "0",
    "status": "success",
    "page": {
        "pageNum": 1,
        "pageSize": 5,
        "total": 100,
        "totalPages": 20
    },
    "data": [...]
}
```

### 9. XSS 防护

```yaml
copilot:
  filter:
    xss-enabled: true
```

- 输入时自动过滤 `<script>` 等危险标签
- 输出时自动转义 HTML 标签
- VO 类标注 `@UnescapeHtml` 可跳过转义

### 10. Redis 缓存双写一致性

```java
@DeleteMapping("/{id}")
@CacheEvict(keys = "user_#{id}")
public Result delete(@PathVariable Long id) {
    userService.delete(id);
    return Results.success().build();
}
```

支持 Spring EL 表达式，执行前后各删除一次缓存。

### 11. Tomcat 线程池监控

```yaml
copilot:
  filter:
    pool-statistic: true
```

访问 `http://localhost:8080/tomcat/threadpool` 查看线程池状态。

### 12. RequestBody 可重复读取

```yaml
copilot:
  filter:
    repeated-read: true
```

允许同一个 Controller 方法使用多个 `@RequestBody` 参数。

## 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `copilot.mvc.cors-enabled` | boolean | false | 是否启用 CORS |
| `copilot.mvc.rest-exception-advice-enabled` | boolean | true | 是否启用全局异常处理 |
| `copilot.mvc.api-sign.enabled` | boolean | false | 是否启用接口签名 |
| `copilot.filter.repeated-read` | boolean | false | RequestBody 可重复读取 |
| `copilot.filter.pool-statistic` | boolean | false | Tomcat 线程池监控 |
| `copilot.filter.xss-enabled` | boolean | false | XSS 防护 |
| `copilot.filter.tenant.mandatory` | boolean | false | 是否强制要求租户ID |
| `copilot.websocket.enabled` | boolean | false | WebSocket 支持 |
| `copilot.websocket.path-prefix` | String | /ws/push/** | WebSocket 路径前缀 |
| `copilot.locale.enabled` | boolean | false | 国际化支持 |
| `copilot.idempotent.enabled` | boolean | true | 接口幂等性 |
| `copilot.cache.enabled` | boolean | false | 缓存功能 |

## 依赖说明

本 Starter 依赖以下模块：
- `copilot-spring-boot-web`：Web 核心组件
- `copilot-spring-boot-starter`：基础 Starter
- `copilot-cache`：Redis 缓存支持
- `copilot-json`：JSON 序列化支持
