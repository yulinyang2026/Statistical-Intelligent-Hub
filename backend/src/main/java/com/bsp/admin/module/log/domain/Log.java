package com.bsp.admin.module.log.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（对齐 15-系统设置与操作日志.md 六字段定义）
 */
@Data
public class Log {

    /** 主键（仓储自增） */
    private Long id;

    /** 操作时间 */
    private LocalDateTime time;

    /** 操作人（中文姓名） */
    private String operator;

    /** 模块标识（task / module / topic / …） */
    private String module;

    /** 操作对象（如「task7 报表导出优化」） */
    private String target;

    /** 动作（如「看板写回·status」） */
    private String action;

    /** 详情（如「进行中 → 已完成」） */
    private String detail;
}
