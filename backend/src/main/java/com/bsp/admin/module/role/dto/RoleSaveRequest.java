package com.bsp.admin.module.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 角色新增/修改入参（id 为空 = 新增；内置角色仅允许改名称/备注/状态）
 */
public record RoleSaveRequest(

        Long id,

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 50, message = "角色名称不能超过 50 个字符")
        String name,

        @NotBlank(message = "角色编码不能为空")
        @Size(max = 50, message = "角色编码不能超过 50 个字符")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "角色编码只能包含字母、数字、下划线和中划线")
        String code,

        /** 数据范围：1 全部 / 2 本组织及下级 / 3 仅本组织 / 4 仅本人 / 5 本人+直属下属 / 6 自定义部门 */
        Integer dataScope,

        /** 数据范围 = 6 时指定的部门集合 */
        List<Long> customOrgIds,

        @Size(max = 200, message = "备注不能超过 200 个字符")
        String remark,

        /** 0 停用 / 1 启用 */
        Integer status
) {
}
