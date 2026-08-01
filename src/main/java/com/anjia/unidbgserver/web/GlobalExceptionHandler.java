package com.anjia.unidbgserver.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器
 *
 * 统一所有 Controller 的错误响应结构 {success, message}，
 * 避免各 Controller 各自返回不同的错误形态（裸 {"error"}、空成功响应、
 * 默认 Spring 错误页等）。内部堆栈仅记录日志，不向客户端暴露。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e, WebRequest request) {
        log.warn("参数错误: {} - {}", e.getMessage(), request.getDescription(false));
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** 缺少必填请求参数（如 @RequestParam 未传）→ 400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return error(HttpStatus.BAD_REQUEST, "缺少必填参数: " + e.getParameterName());
    }

    /** 参数类型不匹配（如 Integer 收到非数字）→ 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {} = {}", e.getName(), e.getValue());
        return error(HttpStatus.BAD_REQUEST, "参数类型不正确: " + e.getName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e, WebRequest request) {
        log.error("请求处理异常 - {}", request.getDescription(false), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message != null ? message : status.getReasonPhrase());
        return ResponseEntity.status(status).body(body);
    }
}
