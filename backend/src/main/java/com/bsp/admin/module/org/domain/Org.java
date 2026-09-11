package com.bsp.admin.module.org.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织机构（部门树，parent_id + path 描述层级，百级数据量）
 */
@Data
public class Org {

    /** 主键 */
    private Long id;

    /** 上级部门，顶级为 0 */
    private Long parentId;

    /** 部门名称 */
    private String name;

    /** 部门编码，唯一 */
    private String code;

    /** 部门负责人（可空） */
    private Long leaderUserId;

    /** 同层级排序 */
    private Integer sort;

    /** 0 停用 / 1 启用 */
    private Integer status;

    /** 祖先路径，如 1/3/7 */
    private String path;

    /** 层级深度（顶级 1） */
    private Integer level;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
