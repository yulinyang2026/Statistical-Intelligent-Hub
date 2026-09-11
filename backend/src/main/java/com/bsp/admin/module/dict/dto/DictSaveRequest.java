package com.bsp.admin.module.dict.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 字典新增/修改入参（id 为空 = 新增；编码仅新增时可设，修改时不可变）
 */
public record DictSaveRequest(
        Long id,
        @NotBlank(message = "字典编码不能为空") String code,
        @NotBlank(message = "字典名称不能为空") String name,
        String description,
        Integer sort
) {
}
