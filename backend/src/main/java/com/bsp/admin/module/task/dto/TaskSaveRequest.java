package com.bsp.admin.module.task.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 任务新增/修改入参（id 为空 = 新增；子任务继承父任务归属与子系统）
 */
public record TaskSaveRequest(
        String id,
        @NotBlank(message = "任务名称不能为空") String title,
        String desc,
        String projectId,
        String moduleId,
        String topicId,
        String subsystemId,
        @NotBlank(message = "执行人不能为空") String assignee,
        @NotBlank(message = "优先级不能为空") String priority,
        String deadline,
        Integer hours,
        Integer week,
        String planDate,
        @NotBlank(message = "任务状态不能为空") String status,
        String group,
        String parentId
) {
}
