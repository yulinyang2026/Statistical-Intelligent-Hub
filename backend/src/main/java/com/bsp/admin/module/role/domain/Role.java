package com.bsp.admin.module.role.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色（权限集合，直接分配给用户）
 */
@Data
public class Role {

    private Long id;

    /** 角色名称 */
    private String name;

    /** 角色编码，唯一（如 R_SUPER） */
    private String code;

    /** 1 内置（不可删）/ 2 自定义 */
    private Integer type;

    /** 数据范围：1 全部 / 2 本组织及下级 / 3 仅本组织 / 4 仅本人 / 5 本人+直属下属 / 6 自定义部门 */
    private Integer dataScope;

    /** 数据范围 = 6 时指定的部门集合 */
    private List<Long> customOrgIds;

    /** 备注 */
    private String remark;

    /** 0 停用 / 1 启用（停用后成员权限立即失效） */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
