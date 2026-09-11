package com.bsp.admin.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 重置密码入参
 */
public record UserResetPasswordRequest(

        @NotNull(message = "用户不能为空")
        Long userId,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 32, message = "密码长度须为 8-32 位")
        String password
) {
}
