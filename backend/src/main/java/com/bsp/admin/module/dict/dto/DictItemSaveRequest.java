package com.bsp.admin.module.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 字典项新增/修改入参（id 为空 = 新增；code 仅新增时可设，修改时不可变）
 */
public record DictItemSaveRequest(
        Long id,
        @NotNull(message = "所属字典不能为空") Long dictId,
        @NotBlank(message = "字典项编码不能为空") String code,
        @NotBlank(message = "显示名不能为空") String name,
        Map<String, Object> ext,
        Integer sort
) {
}
