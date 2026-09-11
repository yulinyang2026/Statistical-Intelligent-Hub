package com.bsp.admin.module.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 离职处理入参（禁用用户与账号、解除部门归属、保留历史引用）
 */
public record UserLeaveRequest(

        @NotNull(message = "用户不能为空")
        Long userId
) {
}
