package com.bsp.admin.module.mgmt.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 功能模块（05-模块管理主实体；原 belong 垫底数据源 m1/m2 已迁移并入本实体）
 */
@Data
public class Module {

    /** 主键，前缀 m */
    private String id;

    /** 模块名称 */
    private String name;

    /** 归属产品（引用 product_belong 字典项 name） */
    private String product;

    /** 模块分类（引用 module_type 字典项 name） */
    private String category;

    /** 负责部门 */
    private String dept;

    /** 负责小组 */
    private String team;

    /** 模块负责人（存姓名） */
    private String owner;

    /** 研发负责人（多，存姓名） */
    private List<String> rd;

    /** 测试负责人（存姓名） */
    private String test;

    /** 成本对象（哒咔） */
    private String cost;

    /** 状态（引用 module_status 字典项 name，由任务待办自动流转：待办>0→活跃，=0→非活跃） */
    private String status;

    /** 干系人（多，存姓名） */
    private List<String> stakeholders;

    /** 模块描述 */
    private String desc;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
