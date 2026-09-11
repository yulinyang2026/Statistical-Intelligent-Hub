package com.bsp.admin.auth;

import com.bsp.admin.common.response.BaseResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录态校验拦截器
 *
 * <p>兼容前端两种取值：{@code Authorization: <token>} 与 {@code Authorization: Bearer <token>}。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Long userId = tokenService.resolve(extractToken(request));
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(
                    BaseResponse.error(401, "登录已失效，请重新登录")));
            return false;
        }

        AuthContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    /** 提取 token：Authorization 头（去可选 "Bearer " 前缀）→ x-token 头 → ?token= 查询参数（CSV 导出浏览器直开） */
    private String extractToken(HttpServletRequest request) {
        String token = headerToken(request.getHeader("Authorization"));
        if (token != null) {
            return token;
        }
        token = headerToken(request.getHeader("x-token"));
        if (token != null) {
            return token;
        }
        String query = request.getParameter("token");
        return query == null || query.isBlank() ? null : query.trim();
    }

    /** 单个头取值：去可选的 "Bearer " 前缀 */
    private String headerToken(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String token = header.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            token = token.substring("Bearer ".length()).trim();
        }
        return token;
    }
}
