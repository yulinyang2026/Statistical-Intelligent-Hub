package com.bsp.admin.module.chat.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话会话（管理小帮手）
 */
@Data
public class ChatSession {

    /** 主键 */
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 会话标题（取首条用户消息前 20 字） */
    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
