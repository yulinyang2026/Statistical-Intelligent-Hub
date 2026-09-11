package com.bsp.admin.module.role.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-权限关联
 */
@Data
public class RolePermission {

    private Long id;

    private Long roleId;

    private Long permissionId;

    private LocalDateTime createTime;
}
