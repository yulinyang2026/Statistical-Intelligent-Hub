package com.bsp.admin.module.project.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 子系统新增/修改入参（id 为空 = 新增；projectId 仅新增时使用，由接口路径决定）
 */
public record SubsystemSaveRequest(
        String id,
        String projectId,
        @NotBlank(message = "子系统名称不能为空") String name,
        String owner,
        Integer sort,
        // 2026-09-11 用户需求：子系统表单扩字段（成本对象/归属产品/部门/小组/研发/项目经理/测试/状态）
        String cost,
        String product,
        String dept,
        String team,
        String rd,
        String pm,
        String test,
        String status
) {
}
