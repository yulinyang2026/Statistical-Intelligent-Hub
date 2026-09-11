package com.bsp.admin.module.dict.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 枚举项拖动排序入参（2026-09-11 用户需求）：按传入 id 顺序重写 sort 为 1..n。
 */
public record DictItemReorderRequest(
        @NotEmpty(message = "排序列表不能为空") List<Long> ids
) {
}
