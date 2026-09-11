package com.bsp.admin.module.user.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-部门关系（一人多部门，is_primary 有且仅有 1 个为 true）
 */
@Data
public class UserOrg {

    private Long id;

    private Long userId;

    private Long orgId;

    /** 是否主属部门 */
    private Boolean isPrimary;

    private LocalDateTime createTime;
}
