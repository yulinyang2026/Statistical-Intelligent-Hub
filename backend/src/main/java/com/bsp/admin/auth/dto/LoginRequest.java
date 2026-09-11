package com.bsp.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录入参（字段名与前端 Api.Auth.LoginParams 对齐）
 */
public record LoginRequest(

        @NotBlank(message = "用户名不能为空")
        String userName,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
