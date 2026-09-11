package com.bsp.admin.module.role.dto;

import java.util.List;

/**
 * 权限树节点（menu 菜单 / api 接口 / data 数据三类）
 */
public record PermissionTreeNode(
        Long id,
        String code,
        String name,
        String type,
        Long parentId,
        Integer sort,
        List<PermissionTreeNode> children
) {
}
