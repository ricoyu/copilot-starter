# Copilot Spring Security6 Starter

基于 Spring Security 6.2.3 的 Starter，简化配置并提供基础安全组件。

## 功能特性

- ✅ 用户名密码登录认证
- ✅ Token 认证（Bearer Token）
- ✅ 权限验证注解支持（@PreAuthorize、@Secured、@RolesAllowed）
- ✅ 通配符权限支持（user:* 匹配 user:read）
- ✅ URL 白名单配置
- ✅ 图片验证码功能
- ✅ 防重复提交（@AntiDupSubmit）
- ✅ XSS 防护
- ✅ 登录/登出成功失败处理
- ✅ 异常统一处理

## 快速开始

### 1. 引入 Maven 依赖

```xml
<dependency>
    <groupId>com.awesomecopilot</groupId>
    <artifactId>copilot-spring-security6-starter</artifactId>
    <version>${copilot.version}</version>
</dependency>
```

### 2. 启用用户名密码登录

在 `application.yaml` 中配置：

```yaml
copilot:
  security6:
    # 启用用户名密码登录
    user-pass-login:
      enabled: true
      login-url: /login      # 登录URL，默认 /login
      logout-url: /logout    # 登出URL，默认 /logout
      role-prefix: ROLE_     # 角色前缀，默认 ROLE_
    
    # URL白名单（不需要认证即可访问）
    white-list:
      - /oauth/**
      - /public/**
      - /actuator/**
```

### 3. 实现 UserDetailsService

创建一个 `UserDetailsService` 实现类并注册为 Spring Bean：

```java
import com.awesomecopilot.security6.authority.WildcardGrantedAuthority;
import com.awesomecopilot.security6.constants.ThreadLocalSecurityConstants;
import com.awesomecopilot.common.lang.context.ThreadContext;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;

@Service
public class JdbcUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. 从数据库查询用户信息
        SysUser sysUser = userRepository.findByUsername(username);
        if (sysUser == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        
        // 2. 查询用户角色和权限
        List<SysRoleDTO> roles = roleRepository.findRolesByUserId(sysUser.getId());
        List<SysPermissionDTO> permissions = permissionRepository.findPermissionsByUserId(sysUser.getId());
        
        // 3. 将userId放入ThreadContext，方便后续获取当前登录用户ID
        ThreadContext.put(ThreadLocalSecurityConstants.USER_ID, sysUser.getId());
        
        // 4. 构建权限列表
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        
        // 添加角色（必须加ROLE_前缀并转大写）
        roles.forEach(role -> {
            String roleCode = "ROLE_" + role.getRoleCode().toUpperCase();
            // 使用WildcardGrantedAuthority支持通配符权限
            GrantedAuthority authority = new WildcardGrantedAuthority(roleCode);
            authorities.add(authority);
        });
        
        // 添加权限
        permissions.forEach(permission -> {
            // 使用WildcardGrantedAuthority支持通配符权限
            GrantedAuthority authority = new WildcardGrantedAuthority(permission.getCode());
            authorities.add(authority);
        });
        
        return new User(sysUser.getUsername(), sysUser.getPassword(), authorities);
    }
}
```

**重要提示：**
- 角色必须加 `ROLE_` 前缀并转大写
- 使用 `WildcardGrantedAuthority` 而非 `SimpleGrantedAuthority` 以支持通配符权限
- 将 `userId` 放入 `ThreadContext` 以便后续业务代码获取当前用户ID

## 配置详解

### 完整配置示例

```yaml
copilot:
  security6:
    # 启动时清理过期token
    clear-on-start: false
    
    # 应用上下文路径（如有Nginx反向代理前缀）
    context-path: /api
    
    # URL白名单
    white-list:
      - /oauth/**
      - /public/**
      - /swagger-ui/**
      - /v3/api-docs/**
    
    # 用户名密码登录配置
    user-pass-login:
      enabled: true
      login-url: /login
      logout-url: /logout
      role-prefix: ROLE_
    
    # 功能特性配置
    feature:
      # 防重复提交（默认开启）
      anti-duplicate-submit: true
      
      # 限流功能（默认开启）
      rate-limit: true
      
      # 图片验证码功能
      pic-code:
        enabled: false          # 是否启用验证码
        ttl: 5                  # 验证码过期时间（分钟）
```

### 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `copilot.security6.clear-on-start` | boolean | false | 启动时是否清理过期token |
| `copilot.security6.context-path` | String | null | 应用上下文路径或Nginx代理前缀 |
| `copilot.security6.white-list` | List | [] | URL白名单，无需认证即可访问 |
| `copilot.security6.user-pass-login.enabled` | boolean | false | 是否启用用户名密码登录 |
| `copilot.security6.user-pass-login.login-url` | String | /login | 登录URL |
| `copilot.security6.user-pass-login.logout-url` | String | /logout | 登出URL |
| `copilot.security6.user-pass-login.role-prefix` | String | ROLE_ | 角色前缀 |
| `copilot.security6.feature.anti-duplicate-submit` | boolean | true | 是否启用防重复提交 |
| `copilot.security6.feature.rate-limit` | boolean | true | 是否启用限流 |
| `copilot.security6.feature.pic-code.enabled` | boolean | false | 是否启用图片验证码 |
| `copilot.security6.feature.pic-code.ttl` | long | 5 | 验证码过期时间（分钟） |

## 权限验证注解

### 1. @PreAuthorize - 推荐方式

```java
// 检查权限（支持通配符）
@PreAuthorize("hasAuthority('user:read')")
@GetMapping("/users")
public List<User> getUsers() { ... }

// 检查角色（大小写不敏感，自动补全ROLE_前缀）
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/admin/action")
public void adminAction() { ... }

// 多个权限任一满足
@PreAuthorize("hasAnyAuthority('user:read', 'user:write')")
@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) { ... }

// 多个角色任一满足
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@DeleteMapping("/users/{id}")
public void deleteUser(@PathVariable Long id) { ... }
```

### 2. @Secured

```java
// 必须显式写 ROLE_ 前缀
@Secured("ROLE_ADMIN")
@PostMapping("/admin/action")
public void adminAction() { ... }
```

### 3. @RolesAllowed (JSR-250)

```java
// 必须显式写 ROLE_ 前缀
@RolesAllowed({"ROLE_ADMIN", "ROLE_MANAGER"})
@PutMapping("/config")
public void updateConfig() { ... }
```

### 通配符权限说明

当使用 `WildcardGrantedAuthority` 时，支持以下通配符匹配：

| 数据库权限 | 接口要求权限 | 匹配结果 |
|-----------|-------------|---------|
| `user:*` | `user:read` | ✅ 通过 |
| `user:*` | `user:write` | ✅ 通过 |
| `user:*` | `order:read` | ❌ 拒绝 |
| `*` | `any:permission` | ✅ 通过 |
| `user:read` | `user:read` | ✅ 通过 |

## Token 认证

### 请求头格式

```
Authorization: Bearer <token>
```

### Token 生命周期

- 默认有效期：30分钟
- Token 存储在 Redis 中
- 登录成功后返回 token，后续请求携带 token 进行认证

### 获取当前用户信息

#### 使用 UserContextHolder 工具类（推荐）

```java
import com.awesomecopilot.security6.utils.UserContextHolder;

// 获取用户ID（返回Long类型）
Long userId = UserContextHolder.getUserId();

// 获取用户名
String username = UserContextHolder.getUsername();

// 获取完整登录信息
Map<String, Object> loginInfo = UserContextHolder.getLoginInfo();

// 获取登录信息中的特定值
String ip = UserContextHolder.getIp();
Object customValue = UserContextHolder.getLoginInfoValue("customKey");
String customStr = UserContextHolder.getLoginInfoValue("customKey", String.class);

// 获取Access Token
String token = UserContextHolder.getAccessToken();

// 判断是否已登录
boolean isLoggedIn = UserContextHolder.isAuthenticated();
```

#### 原始方式

```java
// 方式1：从ThreadContext获取userId
Object userId = ThreadContext.get(ThreadLocalSecurityConstants.USER_ID);

// 方式2：从SecurityContext获取用户名
String username = SecurityContextHolder.getContext().getAuthentication().getName();

// 方式3：从ThreadContext获取完整登录信息
Map<String, Object> loginInfo = ThreadContext.get(ThreadLocalSecurityConstants.LOGIN_INFO);
```

## 功能特性

### 1. 图片验证码

启用配置：

```yaml
copilot:
  security6:
    feature:
      pic-code:
        enabled: true
        ttl: 5  # 5分钟过期
```

获取验证码：`GET /pic-code?codeId=xxx`

登录时传入验证码参数：`codeId` 和 `code`

### 2. 防重复提交

使用 `@AntiDupSubmit` 注解：

```java
@AntiDupSubmit(1000)  // 1000毫秒内不允许重复提交
@PostMapping("/submit")
public Result submit() { ... }
```

需确保配置 `copilot.security6.feature.anti-duplicate-submit=true`（默认开启）

### 3. XSS 防护

XSS 过滤器默认启用，自动过滤请求中的恶意脚本。

## API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/login` | POST | 用户名密码登录 |
| `/logout` | ANY | 登出 |
| `/pic-code` | GET | 获取图片验证码（需启用） |

## 自定义扩展

### 自定义登录成功处理器

```java
@Bean
public AuthenticationSuccessHandler loginSuccessHandler() {
    return new CustomLoginSuccessHandler();
}
```

### 自定义登录失败处理器

```java
@Bean
public AuthenticationFailureHandler loginFailureHandler() {
    return new CustomLoginFailureHandler();
}
```

### 自定义密码编码器

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

## 注意事项

1. **CSRF 已禁用**：本 Starter 默认禁用 CSRF，适用于 RESTful API
2. **Session 无状态**：采用 `STATELESS` 会话策略，不使用 HttpSession
3. **Token 存储**：依赖 Redis 存储 token 和用户会话信息
4. **角色前缀**：使用 `@Secured` 时必须显式写 `ROLE_` 前缀
5. **通配符权限**：必须使用 `WildcardGrantedAuthority` 才能支持通配符

## 依赖说明

本 Starter 依赖以下模块：
- `copilot-spring-security6`：核心安全组件
- `copilot-cache`：Redis 缓存支持
- `copilot-json`：JSON 序列化支持
- `copilot-web`：Web 工具类

---

## 核心架构（源码级解析）

### 认证全流程（时序）

```
┌─────────┐     POST /login       ┌──────────────────────────────────────┐
│  客户端  │ ──────────────────── → │  Spring Security FilterChain         │
│          │    {username, password} │                                    │
│          │                        │  ① HttpServletRequestRepeatedReadFilter (Body可重复读)
│          │                        │  ② SecurityExceptionFilter           (异常兜底)
│          │                        │  ③ PreAuthenticationFilter           (Token认证)
│          │                        │     └─ 登录请求时跳过(isLoginRequest)
│          │                        │  ④ UsernamePasswordAuthenticationFilter (登录处理)
│          │                        │     ├─ 提取 username/password
│          │                        │     ├─ DaoAuthenticationProvider.authenticate()
│          │                        │     │   └─ UserDetailsService.loadUserByUsername()
│          │                        │     │       └─ 业务实现: 查DB → 加载角色+权限
│          │                        │     ├─ 认证成功 → LoginSuccessHandler
│          │                        │     └─ 认证失败 → LoginFailureHandler
│          │                        │                                    │
│          │  ← { code:0, data:     │                                    │
│          │     "66位随机token" }   │                                    │
└─────────┘                        └──────────────────────────────────────┘


后续请求:
┌─────────┐  Authorization:       ┌──────────────────────────────────────┐
│  客户端  │   Bearer <token>      │  Spring Security FilterChain         │
│          │ ──────────────────── →│                                    │
│          │                        │  ③ PreAuthenticationFilter
│          │                        │     ├─ 从Header取 Authorization
│          │                        │     ├─ 去掉 "Bearer " 前缀
│          │                        │     ├─ AuthUtils.auth(token) → Redis验证
│          │                        │     │   └─ 返回 username (null则token无效)
│          │                        │     ├─ ThreadContext.put(ACCESS_TOKEN, token)
│          │                        │     ├─ ThreadContext.put(USERNAME, username)
│          │                        │     ├─ AuthUtils.loginInfo(token) → 加载loginInfo
│          │                        │     └─ ThreadContext.put(USER_ID, userId)
│          │                        │                                    │
│          │                        │  PreAuthenticatedAuthenticationProvider
│          │                        │     └─ PreAuthenticationUserDetailsService
│          │                        │         └─ AuthUtils.userDetails(token) → Redis
│          │                        │             └─ 返回 User(username, pwd, authorities)
│          │                        │                                    │
│          │                        │  SecurityContext 建立完成
│          │                        │     └─ @PreAuthorize 等注解可正常工作
│          │                        │                                    │
└─────────┘                        └──────────────────────────────────────┘
```

### Redis 数据结构

`AuthUtils.login()` 执行后在 Redis 中创建以下数据（通过 Lua 脚本保证原子性）：

| Redis Key | 类型 | 说明 |
|-----------|------|------|
| `auth:token:username` | Hash | field=token, value=username |
| `auth:token:userdetails` | Hash | field=token, value=序列化的 Spring Security `User` 对象（JSON） |
| `auth:token:authorities` | Hash | field=token, value=序列化的 `GrantedAuthority` 列表（JSON） |
| `auth:token:login:info` | Hash | field=token, value=额外登录信息（IP、userId 等，JSON） |
| `auth:{username}:token` | Set | 该用户名对应的所有 token（支持多端登录场景） |
| `auth:token:ttl:zset` | ZSet | token 与过期时间戳（score），用于定时过期清理 |

**核心设计要点：**
- Token **不是 JWT**，而是 `StringUtils.uniqueKey(66)` 生成的 66 位随机字符串
- 所有用户信息和权限都存储在 **Redis** 中，Token 本身不携带任何业务数据
- 支持主动失效 Token（调用 `AuthUtils.logout(token)` 清除 Redis 数据）
- 支持自动刷新 Token（`redis.auth.auto-refresh` 配置，默认 true）
- Token 过期后自动清理并通过 Redis Pub/Sub 发布通知

### AuthUtils 核心 API

```java
import com.awesomecopilot.cache.auth.AuthUtils;

// ===== 登录（写入Redis）=====
// 在 LoginSuccessHandler 中被调用
AuthUtils.login(
    username,           // 用户名
    accessToken,        // 66位随机token
    30L,                // 过期时间
    TimeUnit.MINUTES,   // 时间单位
    userDetails,        // Spring Security User对象（序列化存入Redis）
    authorities,        // 权限列表（序列化存入Redis）
    loginInfo,          // 额外信息：ip、userId等
    false               // 是否单点登录（true则踢掉该用户其他token）
);

// ===== Token 验证 =====
// 在 PreAuthenticationFilter 中被调用
// 检查token是否存在且未过期，返回对应的username
String username = AuthUtils.auth(accessToken);

// ===== 获取 UserDetails =====
// 在 PreAuthenticationUserDetailsService 中被调用
User user = AuthUtils.userDetails(accessToken, User.class);

// ===== 获取权限列表 =====
List<GrantedAuthority> authorities = AuthUtils.authorities(accessToken, GrantedAuthority.class);

// ===== 获取登录额外信息 =====
Map<String, Object> loginInfo = AuthUtils.loginInfo(accessToken, Map.class);

// ===== 登出（清除Redis）=====
boolean success = AuthUtils.logout(accessToken);

// ===== 清理过期token =====
boolean hasExpired = AuthUtils.clearExpired();

// ===== 根据token获取用户名 =====
String username = AuthUtils.username(accessToken);

// ===== 获取某用户的所有token =====
Set<String> tokens = AuthUtils.tokens(username);
```

### 核心组件详解

#### 过滤器链执行顺序

| 顺序 | 组件 | 类 | 职责 |
|------|------|----|------|
| 1 | XSS过滤 | `XSSFilter` | 过滤请求参数中的恶意脚本（注册为 FilterRegistrationBean，order=MIN_VALUE） |
| 2 | Body重复读 | `HttpServletRequestRepeatedReadFilter` | 包装 Request 使 Body 可重复读取（来自 copilot-web） |
| 3 | 异常兜底 | `SecurityExceptionFilter` | 捕获过滤器链上未处理的异常，代理给 `RestSecurityExceptionAdvice` |
| 4 | 验证码 | `VerifyCodeFilter` | 校验图片验证码（仅当 `pic-code.enabled=true` 时注册） |
| 5 | **Token认证** | `PreAuthenticationFilter` | 从请求头提取 Bearer Token → Redis 验证 → 建立认证上下文 |
| 6 | **登录处理** | `UsernamePasswordAuthenticationFilter` | 拦截 POST /login → 用户名密码认证 → 成功/失败处理 |

#### PreAuthenticationFilter 工作原理

```
请求进入
  │
  ├─ 是登录请求(isLoginRequest)?  → 跳过，返回null，交给UsernamePasswordAuthenticationFilter处理
  │
  ├─ Authorization头为空?  → 响应 TOKEN_MISSING 错误，中断请求
  │
  ├─ 不以 "Bearer " 开头?  → 响应 TOKEN_INVALID 错误，中断请求
  │
  ├─ AuthUtils.auth(token) 返回 null?  → token无效/过期，返回null
  │
  └─ AuthUtils.auth(token) 返回 username
       ├─ ThreadContext.put(ACCESS_TOKEN, token)
       ├─ ThreadContext.put(USERNAME, username)
       ├─ AuthUtils.loginInfo(token) → 加载loginInfo
       ├─ ThreadContext.put(USER_ID, loginInfo.userId)
       └─ 返回 username 作为 PreAuthenticated Principal
           → 触发 PreAuthenticatedAuthenticationProvider
           → 触发 PreAuthenticationUserDetailsService（从Redis加载UserDetails+authorities）
           → SecurityContext 建立完成
```

#### LoginSuccessHandler 工作原理

```java
// 登录成功后的处理流程：
// 1. 生成 66 位随机 accessToken
String accessToken = StringUtils.uniqueKey(66);

// 2. 从 SecurityContext 获取认证信息
String username = SecurityContextHolder.getContext().getAuthentication().getName();
User userDetails = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
List<? extends GrantedAuthority> authorities = ...getAuthentication().getAuthorities();

// 3. 构建 loginInfo（包含 ip、userId 等）
Map<String, Object> loginInfo = ThreadContext.get(LOGIN_INFO);
loginInfo.put("ip", ServletUtils.getRemoteRealIP(request));
loginInfo.put("userId", ThreadContext.get(USER_ID));

// 4. 调用 AuthUtils.login() 将所有数据写入 Redis
AuthUtils.login(username, accessToken, 30L, TimeUnit.MINUTES,
               userDetails, authorities, loginInfo, false);

// 5. 响应 Token 给客户端
Result result = Results.success().data(accessToken).build();
RestUtils.writeJson(response, result);
```

**关键点：** `UserDetailsService.loadUserByUsername()` 中需要通过 `ThreadContext.put(ThreadLocalSecurityConstants.USER_ID, userId)` 将 userId 放入 ThreadLocal，`LoginSuccessHandler` 会从中取出并写入 Redis 的 loginInfo。

#### LogoutSuccessHandler 工作原理

```
请求 /logout
  │
  ├─ 从请求头取 Authorization
  ├─ 去掉 "Bearer " 前缀
  ├─ AuthUtils.username(token) → 获取用户名
  ├─ AuthUtils.logout(token)   → 清除 Redis 中所有相关数据
  │   └─ 清除 auth:token:username、auth:token:userdetails、
  │      auth:token:authorities、auth:token:login:info、
  │      auth:{username}:token、auth:token:ttl:zset
  │   └─ 清除 token:authentication 哈希
  │   └─ 发布登出通知到 auth:logout:channel
  └─ 响应成功/失败结果
```

#### WildcardGrantedAuthority 通配符匹配规则

```java
// 权限匹配逻辑（WildcardGrantedAuthority.implies()）：
// 1. 精确匹配（大小写不敏感）
// 2. 通配符匹配：如果权限以 ":*" 结尾，则匹配该前缀下的所有权限

// 示例：
// 数据库配置 "sys:*" → 可匹配:
//   "sys:user:list" ✅
//   "sys:user:save" ✅
//   "sys:menu:delete" ✅
//   "order:read" ❌（前缀不匹配）

// 数据库配置 "*" → 可匹配任何权限

// 在 @PreAuthorize 中使用:
@PreAuthorize("hasPermission('sys:user:list')")  // 使用 hasPermission 而非 hasAuthority
```

**注意：** 通配符匹配通过 `WildcardMethodSecurityExpressionRoot.hasPermission()` 方法实现，所以必须使用 `hasPermission()` 表达式而非 `hasAuthority()`。

### CopilotWebSecurityAutoConfig 自动配置清单

引入 Starter 后，以下 Bean 会被自动注册：

| Bean | 条件 | 说明 |
|------|------|------|
| `SecurityFilterChain` | 无条件 | 主过滤器链（CSRF禁用、无状态Session、白名单、认证过滤器） |
| `AuthenticationManager` | 无条件 | 认证管理器（包含 PreAuth + Dao 两个 Provider） |
| `PasswordEncoder` | `@ConditionalOnMissingBean` | BCrypt 编码器（业务未自定义时使用默认） |
| `PreAuthenticationFilter` | 无条件 | Token 认证过滤器 |
| `UsernamePasswordAuthenticationFilter` | 无条件 | 登录处理过滤器 |
| `DaoAuthenticationProvider` | `user-pass-login.enabled=true` | 用户名密码认证 Provider |
| `PreAuthenticatedAuthenticationProvider` | 无条件 | Token 预认证 Provider |
| `PreAuthenticationUserDetailsService` | `@ConditionalOnMissingBean` | Token 认证时加载 UserDetails |
| `LoginSuccessHandler` | 无条件 | 登录成功处理 |
| `LoginFailureHandler` | 无条件 | 登录失败处理 |
| `LogoutSuccessHandler` | 无条件 | 登出处理 |
| `RestAuthenticationEntryPoint` | 无条件 | 401 未认证处理 |
| `RestAccessDeniedHandler` | 无条件 | 403 权限不足处理 |
| `RestSecurityExceptionAdvice` | `@ConditionalOnMissingBean` | 安全异常统一处理 |
| `GrantedAuthorityDefaults` | 无条件 | 角色前缀配置（默认 ROLE_） |
| `MethodSecurityExpressionHandler` | 无条件 | 通配符权限表达式处理器 |
| `VerifyCodeFilter` | `pic-code.enabled=true` | 验证码过滤器 |
| `VerifyCodeController` | `pic-code.enabled=true` | 验证码 Controller |
| `XSSFilter` | 无条件 | XSS 防护（FilterRegistrationBean） |
| `SecurityExceptionFilter` | 无条件 | 异常兜底过滤器 |

### SecurityFilterChain 核心配置

```java
// 自动配置的 SecurityFilterChain 关键行为：

// 1. 禁用 CSRF（RESTful API 不需要）
http.csrf(AbstractHttpConfigurer::disable);

// 2. 无状态 Session（不创建 HttpSession）
http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

// 3. 异常处理：未认证返回 JSON 格式错误（非重定向到登录页）
http.exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint));

// 4. 授权规则
http.authorizeHttpRequests(authorize -> {
    authorize.requestMatchers(whiteList).permitAll();  // 白名单放行
    authorize.anyRequest().authenticated();             // 其他都需要认证
});

// 5. 登录处理
http.formLogin(form -> {
    form.loginProcessingUrl("/login");  // 登录URL
});

// 6. 登出处理
http.logout(logout -> {
    logout.logoutUrl("/logout");
    logout.logoutSuccessHandler(logoutSuccessHandler());
});
```

### 方法级权限控制总览

Starter 通过 `@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)` 启用了三类注解：

| 注解 | 示例 | 说明 |
|------|------|------|
| `@PreAuthorize` | `@PreAuthorize("hasPermission('sys:user:list')")` | **推荐**，支持 SpEL 表达式和通配符权限 |
| `@PreAuthorize` | `@PreAuthorize("hasRole('ADMIN')")` | 角色检查（自动补 ROLE_ 前缀） |
| `@Secured` | `@Secured("ROLE_ADMIN")` | 角色检查（需显式写 ROLE_ 前缀） |
| `@RolesAllowed` | `@RolesAllowed({"ROLE_ADMIN"})` | JSR-250 标准角色检查 |

### 微服务场景下的使用模式

#### 模式一：认证中心（如 admin-service）

完整启用登录 + Token 认证 + 权限检查：

```yaml
copilot:
  security6:
    white-list:
      - /login          # 登录接口不需要认证
    user-pass-login:
      enabled: true     # 开启用户名密码登录
```

需要提供：
- `UserDetailsService` 实现（从数据库加载用户+角色+权限）

#### 模式二：仅授权（其他微服务）

不开启登录功能，仅做 Token 验证 + 权限检查：

```yaml
copilot:
  security6:
    user-pass-login:
      enabled: false    # 不开启登录功能
```

**工作原理：**
- `PreAuthenticationFilter` 仍然生效，从请求头取 Token 并在 Redis 验证
- `PreAuthenticationUserDetailsService` 从 Redis 加载 UserDetails 和权限
- `@PreAuthorize` 注解正常工作
- 不注册 `DaoAuthenticationProvider`，不处理 `/login` 请求
- Token 由认证中心（admin-service）登录后获取，通过网关或 Feign 传递

**注意：** 此模式下 `UserDetailsService` Bean 不是必需的。
