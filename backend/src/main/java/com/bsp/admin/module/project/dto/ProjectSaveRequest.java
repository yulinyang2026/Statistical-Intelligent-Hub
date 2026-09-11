package com.bsp.admin.module.project.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * 项目新增/修改入参（id 为空 = 新增；必填项对齐 FR-PROJ-004）
 */
public record ProjectSaveRequest(
        String id,
        @NotBlank(message = "项目名称不能为空") String name,
        @NotBlank(message = "成本对象不能为空") String cost,
        @NotBlank(message = "重要级别不能为空") String level,
        @NotBlank(message = "归属产品不能为空") String product,
        String productLine,
        @NotBlank(message = "负责部门不能为空") String dept,
        @NotBlank(message = "负责小组不能为空") String team,
        String sale,
        String pm,
        @NotBlank(message = "项目负责人不能为空") String owner,
        String rd,
        String test,
        @NotBlank(message = "项目状态不能为空") String status,
        BigDecimal budget,
        String startDate,
        String endDate,
        List<String> stakeholders,
        String desc
) {
}
