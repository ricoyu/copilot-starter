package com.awesomecopilot.security6.expression;

import com.awesomecopilot.security6.authority.WildcardGrantedAuthority;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

/**
 * 自定义方法安全表达式根对象
 * <p>
 * 继承 SecurityExpressionRoot 获得所有标准安全方法(hasRole, hasAuthority等),
 * 实现 MethodSecurityExpressionOperations 以支持 @PreAuthorize / @PostAuthorize 注解中的 SpEL 表达式,
 * 同时扩展了通配符权限匹配的单参数 hasPermission(String) 方法。
 * <p>
 * 支持:
 * <ul>
 *   <li>{@code @PreAuthorize("hasPermission('sys:menu:save')")} - 通配符权限匹配</li>
 *   <li>所有标准 SpEL 安全表达式: hasRole, hasAnyRole, isAnonymous, isAuthenticated 等</li>
 * </ul>
 */
public class WildcardMethodSecurityExpressionRoot extends SecurityExpressionRoot
        implements MethodSecurityExpressionOperations {

    private Object filterObject;
    private Object returnObject;

    public WildcardMethodSecurityExpressionRoot(Authentication authentication) {
        super(authentication);
    }

    /**
     * 单参数 hasPermission - 通配符权限匹配核心方法
     * <p>
     * 支持 @PreAuthorize("hasPermission('sys:menu:save')") 调用方式,
     * 委托给 WildcardGrantedAuthority 的通配符匹配逻辑
     */
    public boolean hasPermission(String authority) {
        return getAuthentication().getAuthorities().stream()
                .anyMatch(granted -> {
                    if (granted instanceof WildcardGrantedAuthority) {
                        return ((WildcardGrantedAuthority) granted).implies(() -> authority);
                    }
                    return granted.getAuthority().equals(authority);
                });
    }

    @Override
    public boolean hasPermission(Object target, Object permission) {
        if (permission instanceof String permStr) {
            return hasPermission(permStr);
        }
        return false;
    }

    @Override
    public boolean hasPermission(Object targetId, String targetType, Object permission) {
        if (permission instanceof String permStr) {
            return hasPermission(permStr);
        }
        return false;
    }

    @Override
    public Object getFilterObject() {
        return filterObject;
    }

    @Override
    public void setFilterObject(Object filterObject) {
        this.filterObject = filterObject;
    }

    @Override
    public Object getReturnObject() {
        return returnObject;
    }

    @Override
    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;
    }

    @Override
    public Object getThis() {
        return this;
    }
}