package com.bsp.admin.module.user.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录账号（一人一账号；当前仅口令一种，auth_type 保留扩展位）
 */
@Data
public class Account {

    private Long id;

    /** 归属人员 */
    private Long userId;

    /** 登录名，唯一 */
    private String username;

    /** 认证方式：password（保留扩展位） */
    private String authType;

    /** 口令（BCrypt 加盐哈希，禁止明文/弱哈希；出参一律不返回） */
    private String credential;

    /** 0 停用 / 1 启用（与人员状态联动） */
    private Integer status;

    /** 登录失败计数（内存锁定用，一期随文件存储） */
    private Integer failCount;

    /** 锁定截止时间（防爆破，一期内存语义） */
    private LocalDateTime lockUntil;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
