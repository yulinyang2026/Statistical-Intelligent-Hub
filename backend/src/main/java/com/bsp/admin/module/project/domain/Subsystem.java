package com.bsp.admin.module.project.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 子系统（07-项目下级实体，1 对多；原 belong 垫底数据源 s1/s2 已按原 id 迁移并入本实体）
 */
@Data
public class Subsystem {

    /** 主键（存量沿用 s1/s2，新增按 s+序号递增） */
    private String id;

    /** 所属项目 id */
    private String projectId;

    /** 子系统名称 */
    private String name;

    /** 子系统负责人（存姓名） */
    private String owner;

    /** 成本对象（哒咔编码，2026-09-11 用户需求：子系统表单扩字段） */
    private String cost;

    /** 归属产品（引用 product_belong 字典项 name） */
    private String product;

    /** 负责部门 */
    private String dept;

    /** 负责小组 */
    private String team;

    /** 研发负责人（存姓名） */
    private String rd;

    /** 项目经理（存姓名） */
    private String pm;

    /** 测试负责人（存姓名） */
    private String test;

    /** 项目状态（引用 project_status 字典项 name） */
    private String status;

    /** 排序（越小越靠前） */
    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
