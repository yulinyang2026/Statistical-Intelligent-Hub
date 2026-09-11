package com.bsp.admin.module.dict.dto;

import java.time.LocalDateTime;

/**
 * 字典列表项（含字段数与启用枚举项数统计）
 */
public record DictListItem(
        Long id,
        String code,
        String name,
        String description,
        Integer isBuiltin,
        Integer status,
        Integer sort,
        long fieldCount,
        long itemCount,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
