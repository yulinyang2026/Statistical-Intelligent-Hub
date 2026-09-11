package com.bsp.admin.common.exception;

import com.bsp.admin.common.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：HTTP 200 + code 400，msg 为前端可直接弹出的可读文案 */
    @ExceptionHandler(BizException.class)
    public BaseResponse<Void> handleBiz(BizException e) {
        return BaseResponse.error(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：取第一个字段错误作为文案 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage();
        return BaseResponse.error(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    /** 静态资源 404 交给前端路由（本服务只提供 /api） */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(404)
                .body(BaseResponse.error(ErrorCode.NOT_FOUND.getCode(), "接口不存在"));
    }

    /** 未知异常：HTTP 500 + code 500 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleUnknown(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(500)
                .body(BaseResponse.error(ErrorCode.ERROR.getCode(), "服务器内部错误"));
    }
}
