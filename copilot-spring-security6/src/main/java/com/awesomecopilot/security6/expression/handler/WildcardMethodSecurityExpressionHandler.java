package com.awesomecopilot.security6.expression.handler;

import com.awesomecopilot.security6.expression.WildcardMethodSecurityExpressionRoot;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

/**
 * 支持通配符权限匹配的方法安全表达式处理器
 * <p>
 * 直接使用 {@link WildcardMethodSecurityExpressionRoot} 作为 SpEL 表达式根对象,
 * 该根对象继承 SecurityExpressionRoot 并实现 MethodSecurityExpressionOperations,
 * 确保 SpEL 能正确解析 hasPermission(String) 等自定义方法。
 * <p>
 * 支持:
 * <ul>
 *   <li>{@code @PreAuthorize("hasPermission('sys:menu:save')")} - 通配符权限匹配</li>
 *   <li>{@code @PreAuthorize("hasRole('admin')")} - 标准角色匹配</li>
 * </ul>
 */
public class WildcardMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {

    @Override
    protected MethodSecurityExpressionOperations createSecurityExpressionRoot(
            Authentication authentication, MethodInvocation invocation) {
        // 直接使用自定义根对象, 避免包装器模式导致 SpEL 无法发现 hasPermission(String) 方法
        return new WildcardMethodSecurityExpressionRoot(authentication);
    }
}