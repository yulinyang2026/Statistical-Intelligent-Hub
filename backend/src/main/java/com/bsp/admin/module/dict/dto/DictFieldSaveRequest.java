package com.bsp.admin.module.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 字典字段新增/修改入参（id 为空 = 新增；字段编码仅新增时可设）
 */
public record DictFieldSaveRequest(
        Long id,
        @NotNull(message = "所属字典不能为空") Long dictId,
        @NotBlank(message = "字段编码不能为空") String fieldCode,
        @NotBlank(message = "字段名称不能为空") String fieldName,
        @NotBlank(message = "字段类型不能为空") String fieldType,
        Integer isRequired,
        Integer isUnique,
        List<String> options,
        String defaultValue,
        Integer sort
) {
}
