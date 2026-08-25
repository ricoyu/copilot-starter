# Copilot Spring Boot Starter

Spring Boot 基础 Starter，提供常用功能自动配置。

## 功能特性

- ✅ @RedisListener 注解支持（Redis 订阅）
- ✅ @PostInitialize 注解支持（事务就绪后执行）
- ✅ ApplicationContextHolder（Spring 上下文访问）
- ✅ LocalTime 自动转换
- ✅ 循环依赖自动解决
- ✅ 逻辑删除自动条件
- ✅ Redis 缓存延迟双删
- ✅ 时区自动设置
- ✅ 异步事务支持

## 快速开始

### 1. 引入 Maven 依赖

```xml
<dependency>
    <groupId>com.awesomecopilot</groupId>
    <artifactId>copilot-spring-boot-starter</artifactId>
    <version>${copilot.version}</version>
</dependency>
```

### 2. 基础配置

```yaml
copilot:
  # 设置时区，默认 Asia/Shanghai
  timezone: Asia/Shanghai
  
  # 是否开启异步事务支持，默认 true
  async-transaction: true
  
  # 是否开启 @PostInitialize 注解支持，默认 true
  enable-post-initialize: true
```

## 功能详解

### 1. @RedisListener - Redis 订阅

#### Key 过期订阅

```java
@RedisListener(channelPatterns = "__keyevent@*__:expired")
public void listen(String channel, String message) {
    // channel: __keyevent@0__:expired
    // message: 过期的 key
    log.info("{} 频道上收到消息: {}", channel, message);
}
```

#### PUB/SUB 订阅

```java
@RedisListener(channels = "inbound")
public void broadCastMessage(String channel, String message) {
    System.out.println(channel + ": " + message);
}
```

**启用配置：**

```yaml
copilot:
  cache:
    enabled: true
```

**Redis 配置（src/main/resources/redis.properties）：**

```properties
redis.host=192.168.100.13
redis.password=deepdata$
```

### 2. @PostInitialize - 事务就绪后执行

与 `@PostConstruct` 不同，`@PostInitialize` 在 Spring 事务完全准备好后执行：

```java
@PostInitialize
public void init() {
    // 此时事务已就绪，可以安全执行数据库操作
    userRepository.save(new User());
}
```

### 3. ApplicationContextHolder - 访问 Spring 上下文

```java
// 按名称获取 Bean
LocaleResolver resolver = (LocaleResolver) ApplicationContextHolder.getBean("localeResolver");

// 按类型获取 Bean
CopilotFilterProperties properties = ApplicationContextHolder.getBean(CopilotFilterProperties.class);
```

### 4. LocalTime 自动转换

自动配置 `LocalTimeConverter`，支持 LocalTime 类型的自动转换。

### 5. 循环依赖解决

默认允许循环依赖（等同于 Spring Boot 2.6 之前的行为）：

```properties
spring.main.allow-circular-references=true
```

### 6. 逻辑删除自动条件

引入依赖后，所有 SQL 自动添加 `deleted=0` 条件。

**配置拦截器：**

```yaml
spring:
  jpa:
    properties:
      hibernate:
        session_factory:
          statement_inspector: com.awesomecopilot.cloud.product.config.DeletedTenantIdConditionInterceptor
```

**启用逻辑删除：**

```yaml
copilot:
  orm:
    logical-delete:
      enabled: true  # 默认 false
      field: deleted
```

### 7. Redis 缓存延迟双删

使用 `@CacheEvict` 注解实现延迟双删：

```java
@CacheEvict(keys = "menu_id_name_map")
public void updateMenu() {
    // 更新数据库
    // 自动删除缓存，延迟后再次删除
}
```

**启用配置：**

```yaml
copilot:
  cache:
    enabled: true
```

## 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `copilot.timezone` | String | Asia/Shanghai | 应用时区 |
| `copilot.async-transaction` | boolean | true | 是否开启异步事务支持 |
| `copilot.enable-post-initialize` | boolean | true | 是否开启 @PostInitialize 支持 |
| `copilot.cache.enabled` | boolean | false | 是否开启缓存功能 |
| `copilot.orm.logical-delete.enabled` | boolean | false | 是否开启逻辑删除 |
| `copilot.orm.logical-delete.field` | String | deleted | 逻辑删除字段名 |

## 依赖说明

本 Starter 依赖以下模块：
- `copilot-spring-boot`：核心组件
- `copilot-cache`：Redis 缓存支持
- `commons-spring`：Spring 工具类
