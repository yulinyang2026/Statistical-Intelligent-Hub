package com.bsp.admin.common.exception;

/**
 * 业务异常：统一 HTTP 200 + BaseResponse.code = 400 + 可读 msg
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(ErrorCode.BAD_REQUEST, message);
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
