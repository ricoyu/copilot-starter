package com.awesomecopilot.security6.authority;

import org.springframework.security.core.GrantedAuthority;

/**
 * 支持在数据库中配置的权限包含通配符, 比如user:*,
 * 可以匹配Controller方法@PreAuthorize("hasAuthority(user:read)"), @PreAuthorize("hasAuthority(user:write)")
 * , @PreAuthorize("hasAuthority(user:xxx)")
 * <p/>
 * Copyright: Copyright (c) 2025-05-22 18:27
 * <p/>
 * Company: Sexy Uncle Inc.
 * <p/>
 *
 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public class WildcardGrantedAuthority implements GrantedAuthority {

	private final String role;

	public WildcardGrantedAuthority(String role) {
		this.role = role;
	}

	@Override
	public String getAuthority() {
		return role;
	}

	public boolean implies(GrantedAuthority requiredAuthority) {
		String required = requiredAuthority.getAuthority().toLowerCase();
		if (role.toLowerCase().equals(required)) {
			return true;
		}

		if (role.endsWith(":*")) {
			String prefix = role.substring(0, role.length() - 2).toLowerCase();
			return required.toLowerCase().startsWith(prefix + ":");
		}

		return false;
	}

	@Override
	public String toString() {
		return role;
	}
}