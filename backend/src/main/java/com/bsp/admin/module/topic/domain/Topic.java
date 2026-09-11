package com.bsp.admin.module.topic.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 专题（06-专题管理主实体；替换原 belong 垫底数据源 BelongTopic）
 *
 * <p>与模块的差异：**无 product（归属产品）、无 category（模块分类）**字段（06 文档 §3），其余一致。</p>
 */
@Data
public class Topic {

    /** 主键，前缀 tp */
    private String id;

    /** 专题名称 */
    private String name;

    /** 负责部门 */
    private String dept;

    /** 负责小组 */
    private String team;

    /** 专题负责人（存姓名） */
    private String owner;

    /** 研发负责人（多，存姓名） */
    private List<String> rd;

    /** 测试负责人（存姓名） */
    private String test;

    /** 成本对象 */
    private String cost;

    /** 状态（引用 module_status 字典项 name，由任务待办自动流转：待办>0→活跃，=0→非活跃） */
    private String status;

    /** 干系人（多，存姓名） */
    private List<String> stakeholders;

    /** 专题描述 */
    private String desc;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
