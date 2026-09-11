package com.bsp.admin.module.role.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 角色授权入参（勾选权限集合 + 数据范围设置）
 */
public record RoleAssignPermissionRequest(

        @NotNull(message = "角色不能为空")
        Long roleId,

        /** 勾选的权限 ID 集合（menu / api / data 三类并集） */
        List<Long> permissionIds,

        /** 数据范围 */
        Integer dataScope,

        /** 数据范围 = 6 时指定的部门集合 */
        List<Long> customOrgIds
) {
}
