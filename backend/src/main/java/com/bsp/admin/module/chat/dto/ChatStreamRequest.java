package com.bsp.admin.module.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 流式对话入参
 */
public record ChatStreamRequest(
        @NotBlank(message = "消息内容不能为空") String content
) {
}
