package com.bsp.admin.module.user.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户详情（表单回显：档案 + 账号 + 部门归属 + 角色）
 */
public record UserDetail(
        Long id,
        String name,
        String employeeNo,
        String mobile,
        String email,
        Long managerUserId,
        Integer status,
        String username,
        List<UserOrgItem> orgs,
        List<Long> orgIds,
        Long primaryOrgId,
        List<Long> roleIds,
        LocalDateTime createTime
) {
}
