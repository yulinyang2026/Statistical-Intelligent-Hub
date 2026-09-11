package com.bsp.admin.module.topic.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 专题新增/修改入参（id 为空 = 新增）；与模块的差异：无 product / category（06 文档 §3）
 */
public record TopicSaveRequest(
        String id,
        @NotBlank(message = "专题名称不能为空") String name,
        @NotBlank(message = "负责部门不能为空") String dept,
        @NotBlank(message = "负责小组不能为空") String team,
        @NotBlank(message = "专题负责人不能为空") String owner,
        List<String> rd,
        String test,
        String cost,
        /** 专题状态：2026-09-11 起表单不再填写，由任务待办自动流转（未传时新建默认「非活跃」） */
        String status,
        List<String> stakeholders,
        String desc
) {
}
