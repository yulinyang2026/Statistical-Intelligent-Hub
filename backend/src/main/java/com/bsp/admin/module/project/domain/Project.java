package com.bsp.admin.module.project.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目（07-项目管理主实体；原 belong 垫底数据源 p1/p2 已按原 id 迁移并入本实体，保任务引用不悬空）
 */
@Data
public class Project {

    /** 主键（存量沿用 p1/p2，新增按 p+序号递增） */
    private String id;

    /** 项目名称 */
    private String name;

    /** 成本对象（哒咔编码） */
    private String cost;

    /** 重要级别 A/B/C/D */
    private String level;

    /** 归属产品（引用 product_belong 字典项 name） */
    private String product;

    /** 所属产品型谱 */
    private String productLine;

    /** 负责部门 */
    private String dept;

    /** 负责小组 */
    private String team;

    /** 销售负责人 */
    private String sale;

    /** 项目经理 */
    private String pm;

    /** 项目负责人 */
    private String owner;

    /** 研发负责人 */
    private String rd;

    /** 测试负责人 */
    private String test;

    /** 项目状态（引用 project_status 字典项 name，人工维护，不自动流转） */
    private String status;

    /** 项目预算（万元） */
    private BigDecimal budget;

    /** 开始日期 YYYY-MM-DD */
    private String startDate;

    /** 结束日期 YYYY-MM-DD */
    private String endDate;

    /** 干系人（多，存姓名） */
    private List<String> stakeholders;

    /** 挂靠类型 module / topic（可空 = 不挂靠） */
    private String belongType;

    /** 挂靠对象 id（模块 id 或专题 id） */
    private String belongId;

    /** 项目描述 */
    private String desc;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
