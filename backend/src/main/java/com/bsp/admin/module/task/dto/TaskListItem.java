package com.bsp.admin.module.task.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务列表项（含派生字段；children 为两级树）
 */
public record TaskListItem(
        String id,
        String title,
        String desc,
        String projectId,
        String moduleId,
        String topicId,
        String subsystemId,
        String assignee,
        String priority,
        String deadline,
        Integer hours,
        Integer week,
        String planDate,
        String status,
        String group,
        String parentId,
        String belongLabel,
        String subsystemName,
        long childCount,
        List<TaskListItem> children,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
