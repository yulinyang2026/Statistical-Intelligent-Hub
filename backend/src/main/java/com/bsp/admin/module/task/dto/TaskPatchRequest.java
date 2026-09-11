package com.bsp.admin.module.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 单字段更新入参（看板拖拽 / 工时双击编辑写回）
 */
public record TaskPatchRequest(
        @NotBlank(message = "字段名不能为空") String field,
        @NotNull(message = "字段值不能为空") Object value
) {
}
