package com.bsp.admin.module.chat.dto;

import java.time.LocalDateTime;

/**
 * 会话列表项
 */
public record ChatSessionItem(
        Long id,
        String title,
        LocalDateTime updateTime
) {
}
