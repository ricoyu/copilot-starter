package com.awesomecopilot.security.expression.handler;

import com.awesomecopilot.security.expression.WildcardMethodSecurityExpressionRoot;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class WildcardMethodSecurityExpressionHandler2 extends DefaultMethodSecurityExpressionHandler {

    @Override
    protected MethodSecurityExpressionOperations createSecurityExpressionRoot(
            Authentication authentication, MethodInvocation invocation) {

        // 获取默认的表达式根
        MethodSecurityExpressionOperations defaultRoot =
                super.createSecurityExpressionRoot(authentication, invocation);

        // 创建我们的自定义根
        WildcardMethodSecurityExpressionRoot customRoot =
                new WildcardMethodSecurityExpressionRoot(authentication);

        // 使用动态代理来组合功能
        return (MethodSecurityExpressionOperations) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{MethodSecurityExpressionOperations.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("hasPermission".equals(method.getName()) && args != null && args.length == 1) {
                            return customRoot.hasPermission((String) args[0]);
                        }
                        // 其他方法委托给默认实现
                        return method.invoke(defaultRoot, args);
                    }
                });
    }
}