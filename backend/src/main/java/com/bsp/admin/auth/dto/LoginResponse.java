package com.bsp.admin.auth.dto;

/**
 * 登录出参（字段名与前端 Api.Auth.LoginResponse 对齐）
 */
public record LoginResponse(String token, String refreshToken) {
}
