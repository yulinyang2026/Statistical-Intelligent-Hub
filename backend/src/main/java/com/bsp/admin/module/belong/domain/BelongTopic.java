package com.bsp.admin.module.belong.domain;

import lombok.Data;

/**
 * 归属数据源 - 专题（简易垫底数据源，05/06/07 模块正式实现后替换）
 */
@Data
public class BelongTopic {

    /** 主键，前缀 t */
    private String id;

    private String name;

    /** 状态：活跃 / 非活跃（由任务待办自动流转） */
    private String status;
}
