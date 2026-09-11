package com.bsp.admin.module.chat.dto;

import java.time.LocalDateTime;

/**
 * 消息列表项
 */
public record ChatMessageItem(
        Long id,
        String role,
        String content,
        LocalDateTime createTime
) {
}
