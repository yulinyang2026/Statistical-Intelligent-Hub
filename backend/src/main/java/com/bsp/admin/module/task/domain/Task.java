package com.bsp.admin.module.task.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务（两级层级；归属项目/模块/专题之一，可为空 = 计划外临时任务）
 */
@Data
public class Task {

    /** 主键，前缀 task */
    private String id;

    /** 任务名称 */
    private String title;

    /** 任务说明 */
    private String desc;

    /** 归属项目（与 moduleId/topicId 三选一，可全为空） */
    private String projectId;

    /** 归属模块 */
    private String moduleId;

    /** 归属专题 */
    private String topicId;

    /** 子系统（仅归属为项目时可选，可空） */
    private String subsystemId;

    /** 执行人（存姓名） */
    private String assignee;

    /** 优先级：高 / 中 / 低 */
    private String priority;

    /** 截止月份 YYYY-MM */
    private String deadline;

    /** 预计工时 */
    private Integer hours;

    /** 计划完成周（本月第几周，选填） */
    private Integer week;

    /** 计划完成日期 YYYY-MM-DD（可空，由周计划拖拽写回） */
    private String planDate;

    /** 状态：待处理 / 进行中 / 已完成 / 受阻 */
    private String status;

    /** 归属小组 */
    private String group;

    /** 父任务（仅两级） */
    private String parentId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
