package com.bsp.admin.module.menu.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 左侧菜单自定义顺序（2026-09-10 用户需求：功能树支持拖动排序，顺序全局共享）
 *
 * <p>单条记录（id 固定 menu-order），paths 为菜单路径的显示顺序（一级与子菜单同列，按 DFS 顺序）。</p>
 */
@Data
public class MenuOrder {

    /** 固定主键 */
    private String id;

    /** 菜单路径顺序（未列出的路径按默认顺序排在最后） */
    private List<String> paths;

    private LocalDateTime updateTime;
}
