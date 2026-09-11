package com.bsp.admin.module.dict.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典定义（枚举集合的元数据；内置字典锁定不可删）
 */
@Data
public class Dict {

    /** 主键 */
    private Long id;

    /** 字典编码，唯一，小写蛇形，如 project_status */
    private String code;

    /** 字典名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 1=内置字典（不可删，仅可停用） */
    private Integer isBuiltin;

    /** 1=启用 0=停用 */
    private Integer status;

    /** 展示顺序 */
    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 1=逻辑删除 */
    private Integer deleted;
}
