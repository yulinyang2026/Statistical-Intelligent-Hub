package com.bsp.admin.module.user.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户列表项（分页记录）
 */
public record UserListItem(
        Long id,
        String name,
        String employeeNo,
        String mobile,
        String email,
        Long managerUserId,
        String managerName,
        Integer status,
        String username,
        /** 部门归属（主属在前） */
        List<UserOrgItem> orgs,
        List<String> roleNames,
        /** 是否为其所属机构的负责人（组织管理页「身份」列，2026-09-11） */
        boolean orgLeader,
        LocalDateTime createTime
) {
}
