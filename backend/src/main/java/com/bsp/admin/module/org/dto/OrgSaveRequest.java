package com.bsp.admin.module.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 组织机构新增/修改入参（id 为空 = 新增）
 */
public record OrgSaveRequest(

        Long id,

        @NotNull(message = "上级部门不能为空")
        Long parentId,

        @NotBlank(message = "部门名称不能为空")
        @Size(max = 50, message = "部门名称不能超过 50 个字符")
        String name,

        @NotBlank(message = "部门编码不能为空")
        @Size(max = 50, message = "部门编码不能超过 50 个字符")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "部门编码只能包含字母、数字、下划线和中划线")
        String code,

        /** 部门负责人（可空） */
        Long leaderUserId,

        /** 同层级排序 */
        Integer sort,

        /** 0 停用 / 1 启用 */
        Integer status
) {
}
