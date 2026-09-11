package com.bsp.admin.auth;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Token 会话（一期：进程内存，登出即失效；二期引入 Redis 后升级为服务端会话）
 */
@Service
public class TokenService {

    /** token -> userId */
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    /** 登录成功发放 token（UUID，无业务含义） */
    public String issue(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, userId);
        return token;
    }

    /** 解析 token；无效或已登出返回 null */
    public Long resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return sessions.get(token);
    }

    /** 登出：吊销 token */
    public void revoke(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}
