package com.awesomecopilot.security6.mixin;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * <p/>
 * Copyright: Copyright (c) 2025-05-23 13:34
 * <p/>
 * Company: Sexy Uncle Inc.
 * <p/>

 * @author Rico Yu  ricoyu520@gmail.com
 * @version 1.0
 */
public abstract class WildcardGrantedAuthorityMixIn {

	public WildcardGrantedAuthorityMixIn(@JsonProperty("authority") String role) {}
}
