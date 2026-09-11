package com.bsp.admin.module.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改密码入参（个人中心，2026-09-10）
 */
public record PasswordChangeRequest(
        @NotBlank(message = "原密码不能为空") String oldPassword,
        @NotBlank(message = "新密码不能为空") String newPassword
) {
}
