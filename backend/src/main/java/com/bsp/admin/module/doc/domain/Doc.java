package com.bsp.admin.module.doc.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档（10-文档中心；Markdown 存储，归属项目/模块/专题三选一，本期不含物理附件）
 */
@Data
public class Doc {

    /** 主键，前缀 doc */
    private String id;

    /** 文档标题 */
    private String title;

    /** 归属项目（与 moduleId/topicId 三选一，必选其一） */
    private String projectId;

    /** 归属模块 */
    private String moduleId;

    /** 归属专题 */
    private String topicId;

    /** 正文（Markdown） */
    private String content;

    /** 更新人（存姓名） */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 字数（正文字符数，不含空白） */
    private Integer wordCount;
}
