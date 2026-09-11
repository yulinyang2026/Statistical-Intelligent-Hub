package com.bsp.admin.module.org.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 部门人员批量转移入参（ORG-04：部门合并/调整时其下人员转移到目标部门）
 */
public record OrgTransferRequest(

        @NotNull(message = "源部门不能为空")
        Long fromOrgId,

        @NotNull(message = "目标部门不能为空")
        Long toOrgId
) {
}
