package com.bsp.admin.module.role.dto;

import java.util.List;

/**
 * 角色已分配权限（回显：权限勾选集合 + 数据范围设置）
 */
public record RolePermissionResponse(
        Long roleId,
        List<Long> permissionIds,
        Integer dataScope,
        List<Long> customOrgIds
) {
}
