package com.bsp.admin.module.user.dto;

/**
 * 更换头像入参（个人中心，2026-09-10；前端 canvas 压缩后传 data URL）
 */
public record AvatarUpdateRequest(String avatar) {
}
