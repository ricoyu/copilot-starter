package com.awesomecopilot.security6.expression.handler;

import com.awesomecopilot.security6.expression.WildcardMethodSecurityExpressionRoot;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

public class WildcardMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {

    @Override
    protected MethodSecurityExpressionOperations createSecurityExpressionRoot(
            Authentication authentication, MethodInvocation invocation) {

        // 获取默认的表达式根
        MethodSecurityExpressionOperations defaultRoot =
                super.createSecurityExpressionRoot(authentication, invocation);

        // 返回包装器
        return new WildcardMethodSecurityExpressionRootWrapper(defaultRoot, authentication);
    }

    private static class WildcardMethodSecurityExpressionRootWrapper
            implements MethodSecurityExpressionOperations {

        private final MethodSecurityExpressionOperations delegate;
        private final WildcardMethodSecurityExpressionRoot customRoot;
        private final Authentication authentication;

        public WildcardMethodSecurityExpressionRootWrapper(
                MethodSecurityExpressionOperations delegate,
                Authentication authentication) {
            this.delegate = delegate;
            this.authentication = authentication;
            this.customRoot = new WildcardMethodSecurityExpressionRoot(authentication);
        }

        // 实现SecurityExpressionOperations的抽象方法
        @Override
        public Authentication getAuthentication() {
            return authentication;
        }

        // 重写hasAuthority方法
        @Override
        public boolean hasAuthority(String authority) {
            return customRoot.hasPermission(authority);
        }

        // 实现所有其他方法，委托给默认实现
        @Override
        public boolean hasAnyAuthority(String... authorities) {
            return delegate.hasAnyAuthority(authorities);
        }

        @Override
        public boolean hasRole(String role) {
            return delegate.hasRole(role.toUpperCase());
        }

        @Override
        public boolean hasAnyRole(String... roles) {
            return delegate.hasAnyRole(roles);
        }

        @Override
        public boolean permitAll() {
            return delegate.permitAll();
        }

        @Override
        public boolean denyAll() {
            return delegate.denyAll();
        }

        @Override
        public boolean isAnonymous() {
            return delegate.isAnonymous();
        }

        @Override
        public boolean isAuthenticated() {
            return delegate.isAuthenticated();
        }

        @Override
        public boolean isRememberMe() {
            return delegate.isRememberMe();
        }

        @Override
        public boolean isFullyAuthenticated() {
            return delegate.isFullyAuthenticated();
        }

        @Override
        public boolean hasPermission(Object target, Object permission) {
            return delegate.hasPermission(target, permission);
        }

        @Override
        public boolean hasPermission(Object targetId, String targetType, Object permission) {
            return delegate.hasPermission(targetId, targetType, permission);
        }

        @Override
        public Object getFilterObject() {
            return delegate.getFilterObject();
        }

        @Override
        public void setFilterObject(Object filterObject) {
            delegate.setFilterObject(filterObject);
        }

        @Override
        public Object getReturnObject() {
            return delegate.getReturnObject();
        }

        @Override
        public void setReturnObject(Object returnObject) {
            delegate.setReturnObject(returnObject);
        }

        @Override
        public Object getThis() {
            return delegate.getThis();
        }
    }
}