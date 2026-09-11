package com.bsp.admin.module.user.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色直接关联（无岗位层）
 */
@Data
public class UserRole {

    private Long id;

    private Long userId;

    private Long roleId;

    private LocalDateTime createTime;
}
