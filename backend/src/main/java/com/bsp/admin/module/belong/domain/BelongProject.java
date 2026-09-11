package com.bsp.admin.module.belong.domain;

import lombok.Data;

/**
 * 归属数据源 - 项目（简易垫底数据源，05/06/07 模块正式实现后替换）
 */
@Data
public class BelongProject {

    /** 主键，前缀 p */
    private String id;

    private String name;

    /** 挂靠模块（可空） */
    private String moduleId;

    /** 挂靠专题（可空） */
    private String topicId;

    /** 状态：活跃 / 非活跃（人工维护） */
    private String status;
}
