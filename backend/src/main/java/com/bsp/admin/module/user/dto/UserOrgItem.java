package com.bsp.admin.module.user.dto;

/**
 * 用户-部门归属项
 */
public record UserOrgItem(Long orgId, String orgName, boolean isPrimary) {
}
