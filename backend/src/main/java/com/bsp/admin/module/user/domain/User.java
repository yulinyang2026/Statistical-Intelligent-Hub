package com.bsp.admin.module.user.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人员档案（与登录账号 Account 分离，业务主键统一 user_id）
 */
@Data
public class User {

    /** 主键（业务主键，禁止用 username 充当） */
    private Long id;

    /** 姓名 */
    private String name;

    /** 工号，唯一 */
    private String employeeNo;

    /** 手机号，唯一（联系信息，不作登录名） */
    private String mobile;

    /** 邮箱 */
    private String email;

    /** 头像（2026-09-10 个人中心支持更换头像；data URL 形式，空 = 使用系统默认头像） */
    private String avatar;

    /** 直属主管（主管链，禁止成环） */
    private Long managerUserId;

    /** 0 停用（含离职）/ 1 启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
