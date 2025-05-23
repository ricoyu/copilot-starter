# 一 用户名密码登录

只需要三板斧

1. 引入Maven依赖

   ```xml
   <dependency>
       <groupId>com.awesomecopilot</groupId>
       <artifactId>copilot-spring-security6-starter</artifactId>
       <version>17.0.0</version>
   </dependency>
   ```

2. application.yaml

   ```yaml
   copilot:
     security6:
       user-pass-login:
         enabled: true
   ```

3. 编写一个JdbcUserDetailsService并注册为Spring Bean

   用户的角色和权限都要查询出来, 角色转大写后加上ROLE_前缀然后在转成WildcardGrantedAuthority

   ```java
   import com.awesomecopilot.cloud.oauth.dto.SysPermissionDTO;
   import com.awesomecopilot.cloud.oauth.dto.SysRoleDTO;
   import com.awesomecopilot.cloud.oauth.entity.SysUser;
   import com.awesomecopilot.common.lang.concurrent.Concurrent;
   import com.awesomecopilot.common.lang.concurrent.FutureResult;
   import com.awesomecopilot.orm.dao.CriteriaOperations;
   import com.awesomecopilot.orm.dao.SQLOperations;
   import com.awesomecopilot.security6.authority.WildcardGrantedAuthority;
   import org.springframework.beans.factory.annotation.Autowired;
   import org.springframework.security.core.GrantedAuthority;
   import org.springframework.security.core.userdetails.User;
   import org.springframework.security.core.userdetails.UserDetails;
   import org.springframework.security.core.userdetails.UserDetailsService;
   import org.springframework.security.core.userdetails.UsernameNotFoundException;
   import org.springframework.stereotype.Service;
   
   import java.util.ArrayList;
   import java.util.Collection;
   import java.util.List;
   
   @Service
   public class JdbcUserDetailsService implements UserDetailsService {
   
   	@Autowired
   	private CriteriaOperations criteriaOperations;
   
   	@Autowired
   	private SQLOperations sqlOperations;
   
   	@Override
   	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
   		SysUser sysUser = criteriaOperations.ensureExists(SysUser.class, "username", username);
   		String roleSql = """
   				select r.* from sys_user u join sys_user_role ur on u.id = ur.user_id
   				JOIN sys_role r on ur.role_id = r.id where u.id=:userId;
   				""";
   		String permissionSql = """
   				select p.* from sys_user_role ur join sys_role_permission rp on ur.role_id=rp.role_id join sys_permission p on rp.permission_id = p.id
   				where ur.user_id=:userId""";
   
   		FutureResult<List<SysRoleDTO>> roleFuture = Concurrent.submit(() -> {
   			return sqlOperations.findList(roleSql,
   					"userId",
   					sysUser.getId(),
   					SysRoleDTO.class);
   		});
   		FutureResult<List<SysPermissionDTO>> permissionFuture = Concurrent.submit(() -> {
   			return sqlOperations.findList(permissionSql,
   					"userId",
   					sysUser.getId(),
   					SysPermissionDTO.class);
   		});
   		Concurrent.await();
   
   		List<SysRoleDTO> roles = roleFuture.get();
   		List<SysPermissionDTO> permissionDTOs = permissionFuture.get();
   
   		Collection<GrantedAuthority> authorities = new ArrayList<>();
   		roles.stream().map((sysRole) -> {
   			String role = "ROLE_" + sysRole.getRoleCode().toUpperCase();
   			GrantedAuthority grantedAuthority = new WildcardGrantedAuthority(role);
   			return grantedAuthority;
   		}).forEach(authority -> authorities.add(authority));
   
   		List<String> permissions = new ArrayList<>();
   		permissionDTOs.stream().map((permission) -> {
   			GrantedAuthority authority = new WildcardGrantedAuthority(permission.getCode());
   			return authority;
   		}).forEach(authority -> authorities.add(authority));
   
   		//User(String username, String password, Collection<? extends GrantedAuthority> authorities)
   		User user = new User(sysUser.getUsername(), sysUser.getPassword(), authorities);
   		return user;
   	}
   }
   ```



# 二 权限验证注解

1. @PreAuthorize("hasAuthority('user:read')")

   用户的数据库权限配置支持*通配符, 比如这边要求有user:read权限, 但是如果数据库中给当前登录用户分配的权限是`user:*`, 那么也是授权通过的, 原生SpringSecurity是不支持通配符的

   要支持通配符权限, 在JdbcUserDetailsService那边将权限字符串转成GrantedAuthority时, 要用WildcardGrantedAuthority这个实现类, 不要用SpringSecurity自带的SimpleGrantedAuthority

   ```java
   @Override
   public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
     SysUser sysUser = criteriaOperations.ensureExists(SysUser.class, "username", username);
     String roleSql = """
         select r.* from sys_user u join sys_user_role ur on u.id = ur.user_id
         JOIN sys_role r on ur.role_id = r.id where u.id=:userId;
         """;
     String permissionSql = """
         select p.* from sys_user_role ur join sys_role_permission rp on ur.role_id=rp.role_id join sys_permission p on rp.permission_id = p.id
         where ur.user_id=:userId""";
   
     FutureResult<List<SysRoleDTO>> roleFuture = Concurrent.submit(() -> {
       return sqlOperations.findList(roleSql,
           "userId",
           sysUser.getId(),
           SysRoleDTO.class);
     });
     FutureResult<List<SysPermissionDTO>> permissionFuture = Concurrent.submit(() -> {
       return sqlOperations.findList(permissionSql,
           "userId",
           sysUser.getId(),
           SysPermissionDTO.class);
     });
     Concurrent.await();
   
     List<SysRoleDTO> roles = roleFuture.get();
     List<SysPermissionDTO> permissionDTOs = permissionFuture.get();
   
     Collection<GrantedAuthority> authorities = new ArrayList<>();
     roles.stream().map((sysRole) -> {
       String role = "ROLE_" + sysRole.getRoleCode().toUpperCase();
       GrantedAuthority grantedAuthority = new WildcardGrantedAuthority(role);
       return grantedAuthority;
     }).forEach(authority -> authorities.add(authority));
   
     List<String> permissions = new ArrayList<>();
     permissionDTOs.stream().map((permission) -> {
       GrantedAuthority authority = new WildcardGrantedAuthority(permission.getCode());
       return authority;
     }).forEach(authority -> authorities.add(authority));
   
     //User(String username, String password, Collection<? extends GrantedAuthority> authorities)
     User user = new User(sysUser.getUsername(), sysUser.getPassword(), authorities);
     return user;
   }
   }
   ```

   

2. @PreAuthorize("hasRole('ADMIN')")

   这边角色名大小写不不敏感, 在WildcardMethodSecurityExpressionRootWrapper#hasRole方法会将角色名转成大写再加上ROLE_前缀后匹配

   所以JdbcUserDetailsService#loadUserByUsername方法里面将角色转成WildcardGrantedAuthority时要加上"ROLE_"前缀并且角色名要转成大写



# 三 集成认证功能

只需要两板斧, 什么配置都不需要, 微服务接入权限控制超级简单

1. 引入Maven依赖

   ```xml
   <dependency>
       <groupId>com.awesomecopilot</groupId>
       <artifactId>copilot-spring-security6-starter</artifactId>
       <version>17.0.0</version>
   </dependency>
   ```

2. Controller方法上加认证注解

   * @Secured 必须显式写 ROLE_ 前缀 (如 ROLE_ADMIN)

   * @PreAuthorize("hasRole('ADMIN')") 会自动补全前缀(实际校验 ROLE_ADMIN)

   * @PreAuthorize("hasAuthority('user:read')") 检查具有某权限, 支持通配符, 比如数据库中给用户赋了权限user:*, 那么该用户就能访问@PreAuthorize("hasAuthority('user:read')")保护的接口了