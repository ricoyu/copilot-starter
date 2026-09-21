# Copilot Spring Boot Starter

Spring Boot 基础 Starter，提供常用功能自动配置。

## 功能特性

- ✅ @RedisListener 注解支持（Redis 订阅）
- ✅ @PostInitialize 注解支持（事务就绪后执行）
- ✅ ApplicationContextHolder（Spring 上下文访问）
- ✅ LocalTime 自动转换
- ✅ 循环依赖默认允许（Boot 2.6+ 默认禁止，starter 补了默认值，详见第 5 节）
- ✅ 逻辑删除自动条件
- ✅ Redis 缓存延迟双删
- ✅ 时区自动设置
- ✅ 异步事务支持
- ✅ 把容器里的 ObjectMapper 交给静态的 JacksonUtils 使用（并应用 copilot.jackson.* 增强配置）

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
redis.password=your-redis-password
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

### 5. 循环依赖（默认允许，21.0.8 起改为显式补位）

Spring Boot 2.6+ 默认禁止循环依赖，而上游 commons-spring 的 `TransactionEvents` 在
`@PostConstruct` 里通过 `getBean` 获取自身，离了这个开关应用无法启动。因此本 starter 用
`CopilotDefaultsEnvironmentPostProcessor` 在应用没有配置该键时补上
`spring.main.allow-circular-references=true`（以最低优先级注入）：
- 不配置：默认允许循环依赖，与旧版本行为一致；
- 应用自己配置了该键（无论 true/false）：必定以应用配置为准；
- 旧版本打包在 jar 根目录的 `application.properties` 已删除——它是否生效取决于 classpath 顺序，且会占掉宿主应用同名文件的位置，现在改为显式补位后这两点都不复存在。

### 6. 逻辑删除自动条件

引入依赖后，所有 SQL 自动添加 `deleted=0` 条件。

**配置拦截器：**

```yaml
spring:
  jpa:
    properties:
      hibernate:
        session_factory:
          statement_inspector: com.awesomecopilot.orm.interceptor.DeletedTenantIdConditionInterceptor
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
| `copilot.async-transaction` | boolean | true | 是否开启异步事务支持（camelCase 写法 `copilot.asyncTransaction` 同样识别） |
| `copilot.enable-post-initialize` | boolean | true | 是否开启 @PostInitialize 支持（camelCase 写法同样识别） |
| `copilot.cache.enabled` | boolean | true | 是否开启缓存功能（classpath 有 JedisUtils 时生效；关闭后 @RedisListener/@CacheEvict 双删/订阅处理全部不装配） |
| `copilot.orm.logical-delete.enabled` | boolean | false | 是否开启逻辑删除 |
| `copilot.orm.logical-delete.field` | String | deleted | 逻辑删除字段名 |
| `copilot.jackson.field-name-quote` | boolean | true | false 时输出 JSON 字段名不带双引号（同时允许解析不带引号的字段名；注意转义设置位于 JsonFactory 层，同源多个 mapper 会共同受影响） |
| `copilot.jackson.serializers` | List | [] | 自定义序列化器，条目为 {type, serializer}，一次性注册进同一个 SimpleModule；实例化失败启动报错 |
| `copilot.jackson.deserializers` | List | [] | 自定义反序列化器，条目为 {type, deserializer}，同上 |

## 依赖说明

本 Starter 依赖以下模块：
- `copilot-spring-boot`：核心组件
- `copilot-cache`：Redis 缓存支持
- `commons-spring`：Spring 工具类
