package com.bsp.admin.module.role.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色列表项
 */
public record RoleListItem(
        Long id,
        String name,
        String code,
        /** 1 内置（不可删）/ 2 自定义 */
        Integer type,
        Integer dataScope,
        List<Long> customOrgIds,
        String remark,
        Integer status,
        /** 挂靠用户数 */
        long userCount,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
