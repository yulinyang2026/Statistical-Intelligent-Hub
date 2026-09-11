package com.bsp.admin.auth.dto;

import java.util.List;

/**
 * 用户信息出参（字段名与前端 Api.Auth.UserInfo 逐字对齐）
 *
 * @param buttons 按钮级权限编码（前端 v-auth 指令消费）
 * @param roles   角色编码（如 R_SUPER）
 */
public record UserInfoResponse(
        List<String> buttons,
        /** 菜单型权限码（2026-09-11 新增）：前端据此过滤左侧功能树（如 menu:system / menu:system:org） */
        List<String> menus,
        List<String> roles,
        Long userId,
        String userName,
        String email,
        String avatar
) {
}
