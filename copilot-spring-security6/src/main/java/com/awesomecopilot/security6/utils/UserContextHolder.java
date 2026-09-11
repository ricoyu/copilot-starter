package com.awesomecopilot.security6.utils;

import com.awesomecopilot.common.lang.context.ThreadContext;
import com.awesomecopilot.security6.constants.ThreadLocalSecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

/**
 * 用户上下文持有者工具类
 * <p>
 * 提供对当前登录用户信息的简化访问方式
 * </p>
 * <p>
 * Copyright: (C), 2024
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
public final class UserContextHolder {

    private static final Logger log = LoggerFactory.getLogger(UserContextHolder.class);

    private UserContextHolder() {
        // 工具类，禁止实例化
    }

    /**
     * 获取当前登录用户的ID
     *
     * @return 用户ID，如果未登录返回null
     */
    public static Long getUserId() {
        Object userId = ThreadContext.get(ThreadLocalSecurityConstants.USER_ID);
        if (userId == null) {
            return null;
        }
        if (userId instanceof Long) {
            return (Long) userId;
        }
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        try {
            return Long.parseLong(userId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取当前登录用户的用户名
     *
     * @return 用户名，如果未登录返回null
     */
    public static String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        return authentication.getName();
    }

    /**
     * 获取完整的登录信息
     *
     * @return 登录信息Map，如果未登录返回null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getLoginInfo() {
        return ThreadContext.get(ThreadLocalSecurityConstants.LOGIN_INFO);
    }

    /**
     * 从登录信息中获取指定key的值
     *
     * @param key 键名
     * @return 值，如果不存在返回null
     */
    public static Object getLoginInfoValue(String key) {
        Map<String, Object> loginInfo = getLoginInfo();
        if (loginInfo == null) {
            return null;
        }
        return loginInfo.get(key);
    }

    /**
     * 从登录信息中获取指定key的值（指定类型）
     *
     * @param key   键名
     * @param clazz 值的类型
     * @param <T>   泛型类型
     * @return 值，如果不存在或类型不匹配返回null
     */
    @SuppressWarnings("unchecked")
    public static <T> T getLoginInfoValue(String key, Class<T> clazz) {
        Object value = getLoginInfoValue(key);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 获取当前用户的Access Token
     *
     * @return Access Token，如果未登录返回null
     */
    public static String getAccessToken() {
        return ThreadContext.get(ThreadLocalSecurityConstants.ACCESS_TOKEN);
    }

    /**
     * 判断当前是否已登录
     *
     * @return true表示已登录，false表示未登录
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    /**
     * 获取当前用户的IP地址
     *
     * @return IP地址，如果未登录或登录信息中没有IP返回null
     */
    public static String getIp() {
        return getLoginInfoValue("ip", String.class);
    }

    /**
     * 设置用户ID到ThreadContext
     * <p>
     * 通常在UserDetailsService实现中使用
     * </p>
     *
     * @param userId 用户ID
     */
    public static void setUserId(Long userId) {
        log.info("setUserId, userId={}", userId);
        ThreadContext.put(ThreadLocalSecurityConstants.USER_ID, userId);
    }

    /**
     * 设置登录信息到ThreadContext
     *
     * @param loginInfo 登录信息
     */
    public static void setLoginInfo(Map<String, Object> loginInfo) {
        log.info("setLoginInfo, loginInfo size={}", loginInfo != null ? loginInfo.size() : 0);
        ThreadContext.put(ThreadLocalSecurityConstants.LOGIN_INFO, loginInfo);
    }

    /**
     * 设置Access Token到ThreadContext
     *
     * @param accessToken Access Token
     */
    public static void setAccessToken(String accessToken) {
        ThreadContext.put(ThreadLocalSecurityConstants.ACCESS_TOKEN, accessToken);
    }

    /**
     * 清除当前用户上下文
     */
    public static void clear() {
        log.info("clear >> 清除用户上下文");
        ThreadContext.remove(ThreadLocalSecurityConstants.USER_ID);
        ThreadContext.remove(ThreadLocalSecurityConstants.USERNAME);
        ThreadContext.remove(ThreadLocalSecurityConstants.ACCESS_TOKEN);
        ThreadContext.remove(ThreadLocalSecurityConstants.LOGIN_INFO);
    }
}
