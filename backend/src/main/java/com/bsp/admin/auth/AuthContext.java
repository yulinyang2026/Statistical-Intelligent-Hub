package com.bsp.admin.auth;

/**
 * 当前请求登录用户上下文（拦截器写入，Controller/Service 读取，请求结束清理）
 */
public final class AuthContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /** 当前登录用户 ID；未登录（如放行的匿名接口）返回 null */
    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
