package com.awesomecopilot.security6.service;

import com.awesomecopilot.cache.auth.AuthUtils;
import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.security6.constants.SecurityConstants;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Token验证用这个
 * <p>
 * Copyright: Copyright (c) 2018-07-24 13:41
 * <p>
 * Company: DataSense
 * <p>
 * @author Rico Yu	ricoyu520@gmail.com
 * @version 1.0
 */
public class PreAuthenticationUserDetailsService implements AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {
	
	@Override
	public UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) throws UsernameNotFoundException {
		String accessToken = ThreadContext.get(SecurityConstants.ACCESS_TOKEN);
		User user = AuthUtils.userDetails(accessToken, User.class);
		return user;
	}

}
