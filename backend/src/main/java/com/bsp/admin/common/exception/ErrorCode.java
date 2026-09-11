package com.bsp.admin.common.exception;

/**
 * 错误码（与前端 ApiStatus 枚举一致）
 */
public enum ErrorCode {

    /** 成功 */
    SUCCESS(200),

    /** 业务错误（可读文案经 msg 返回） */
    BAD_REQUEST(400),

    /** 未登录 / 登录失效 */
    UNAUTHORIZED(401),

    /** 已登录但权限不足 */
    FORBIDDEN(403),

    /** 资源不存在 */
    NOT_FOUND(404),

    /** 服务器错误 */
    ERROR(500);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
