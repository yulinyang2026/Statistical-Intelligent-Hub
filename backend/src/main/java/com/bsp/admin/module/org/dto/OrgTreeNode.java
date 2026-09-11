package com.bsp.admin.module.org.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织机构树节点（部门树展示）
 */
public record OrgTreeNode(
        Long id,
        Long parentId,
        String name,
        String code,
        Long leaderUserId,
        String leaderName,
        Integer sort,
        Integer status,
        String path,
        Integer level,
        /** 在职人员数（含主属与兼职，去重） */
        long userCount,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        List<OrgTreeNode> children
) {
}
