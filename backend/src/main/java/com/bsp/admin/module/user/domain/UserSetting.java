package com.bsp.admin.module.user.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户级界面设置（2026-09-11 用户需求：系统设置按用户存库，每个人可自定义自己的风格）。
 *
 * <p>一条记录对应一个用户；{@code payload} 直接存前端设置 store 的状态子集
 * （主题风格 / 菜单布局 / 菜单风格 / 系统主题色 / 基础配置等），Jackson 会以嵌套对象落盘。</p>
 */
@Data
public class UserSetting {

    /** 主键（仓储自增） */
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 设置内容（前端 setting store 的状态子集） */
    private Map<String, Object> payload;

    private LocalDateTime updateTime;
}
