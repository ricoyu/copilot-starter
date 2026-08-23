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
