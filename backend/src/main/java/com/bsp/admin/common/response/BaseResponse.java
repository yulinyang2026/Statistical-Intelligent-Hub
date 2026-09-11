package com.bsp.admin.common.response;

/**
 * 统一响应体（与前端 BaseResponse 契约逐字对齐）
 *
 * <pre>
 * { "code": 200, "msg": "success", "data": { } }
 * </pre>
 *
 * <ul>
 *   <li>code = 200 成功；400 业务错误；401 未授权（前端自动登出）；403 禁止；404 不存在；500 服务器错误</li>
 *   <li>业务失败统一 HTTP 200 + code 400，避免前端走 HTTP 错误重试逻辑</li>
 * </ul>
 */
public record BaseResponse<T>(int code, String msg, T data) {

    public static <T> BaseResponse<T> ok(T data) {
        return new BaseResponse<>(200, "success", data);
    }

    public static BaseResponse<Void> ok() {
        return new BaseResponse<>(200, "success", null);
    }

    public static <T> BaseResponse<T> error(int code, String msg) {
        return new BaseResponse<>(code, msg, null);
    }
}
