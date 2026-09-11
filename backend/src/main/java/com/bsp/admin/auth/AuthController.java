package com.bsp.admin.auth;

import com.bsp.admin.auth.dto.LoginRequest;
import com.bsp.admin.auth.dto.LoginResponse;
import com.bsp.admin.auth.dto.UserInfoResponse;
import com.bsp.admin.common.response.BaseResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录与鉴权接口
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录（免鉴权，见 WebConfig 拦截排除） */
    @PostMapping("/auth/login")
    public BaseResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return BaseResponse.ok(authService.login(request));
    }

    /** 当前登录用户信息（按钮权限编码 + 角色编码） */
    @GetMapping("/user/info")
    public BaseResponse<UserInfoResponse> getUserInfo() {
        return BaseResponse.ok(authService.getUserInfo(AuthContext.getUserId()));
    }

    /** 登出（吊销当前 token） */
    @PostMapping("/auth/logout")
    public BaseResponse<Void> logout(HttpServletRequest request) {
        authService.logout(request.getHeader("Authorization"));
        return BaseResponse.ok();
    }
}
