package com.bsp.admin.module.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 用户启停入参（用户与账号状态联动）
 */
public record UserStatusRequest(

        @NotNull(message = "用户不能为空")
        Long userId,

        /** 0 停用 / 1 启用 */
        @NotNull(message = "状态不能为空")
        Integer status
) {
}
