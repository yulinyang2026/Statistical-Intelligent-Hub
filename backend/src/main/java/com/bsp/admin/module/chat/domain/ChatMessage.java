package com.bsp.admin.module.chat.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话消息（管理小帮手）
 */
@Data
public class ChatMessage {

    /** 主键 */
    private Long id;

    /** 所属会话 */
    private Long sessionId;

    /** 所属用户 */
    private Long userId;

    /** user=用户提问 assistant=AI 回答 */
    private String role;

    /** 消息内容 */
    private String content;

    private LocalDateTime createTime;
}
