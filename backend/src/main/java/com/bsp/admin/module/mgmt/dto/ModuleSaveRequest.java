package com.bsp.admin.module.mgmt.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 模块新增/修改入参（id 为空 = 新增）
 */
public record ModuleSaveRequest(
        String id,
        @NotBlank(message = "模块名称不能为空") String name,
        @NotBlank(message = "归属产品不能为空") String product,
        @NotBlank(message = "模块分类不能为空") String category,
        @NotBlank(message = "负责部门不能为空") String dept,
        @NotBlank(message = "负责小组不能为空") String team,
        @NotBlank(message = "模块负责人不能为空") String owner,
        List<String> rd,
        String test,
        String cost,
        /**
         * 模块状态：**2026-09-11 起表单不再填写**，改由任务待办自动流转（待办>0→活跃，=0→非活跃）。
         * 未传时新建默认「非活跃」、编辑保持原值；仅在显式传入时做字典校验。
         */
        String status,
        List<String> stakeholders,
        String desc
) {
}
