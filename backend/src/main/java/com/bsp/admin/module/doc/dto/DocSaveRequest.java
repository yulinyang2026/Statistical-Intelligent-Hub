package com.bsp.admin.module.doc.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 文档新增/修改入参（id 为空 = 新增；归属三选一必填其一）
 */
public record DocSaveRequest(
        String id,
        @NotBlank(message = "文档标题不能为空") String title,
        String projectId,
        String moduleId,
        String topicId,
        String content
) {
}
