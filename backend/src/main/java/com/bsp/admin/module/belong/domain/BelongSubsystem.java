package com.bsp.admin.module.belong.domain;

import lombok.Data;

/**
 * 归属数据源 - 子系统（简易垫底数据源，05/06/07 模块正式实现后替换）
 */
@Data
public class BelongSubsystem {

    /** 主键，前缀 s */
    private String id;

    /** 所属项目 */
    private String projectId;

    private String name;
}
