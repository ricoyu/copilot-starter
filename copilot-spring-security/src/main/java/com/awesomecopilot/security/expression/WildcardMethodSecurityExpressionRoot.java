package com.awesomecopilot.security.expression;

import com.awesomecopilot.security.authority.WildcardGrantedAuthority;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.core.Authentication;

public class WildcardMethodSecurityExpressionRoot extends SecurityExpressionRoot {

    public WildcardMethodSecurityExpressionRoot(Authentication authentication) {
        super(authentication);
    }

    public boolean hasPermission(String authority) {
        return getAuthentication().getAuthorities().stream()
                .anyMatch(granted -> {
                    if (granted instanceof WildcardGrantedAuthority) {
                        return ((WildcardGrantedAuthority) granted).implies(() -> authority);
                    }
                    return granted.getAuthority().equals(authority);
                });
    }
}