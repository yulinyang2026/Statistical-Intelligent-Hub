package com.bsp.admin.module.role.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限（最小授权单元：menu 菜单 / api 接口 / data 数据）
 */
@Data
public class Permission {

    private Long id;

    /** 权限码（集中维护，禁止业务代码硬编码），如 system:user:create */
    private String code;

    /** 权限名称 */
    private String name;

    /** menu / api / data */
    private String type;

    /** 上级权限（顶级为 0；menu 类型按菜单层级组织） */
    private Long parentId;

    /** 同层级排序 */
    private Integer sort;

    private LocalDateTime createTime;
}
