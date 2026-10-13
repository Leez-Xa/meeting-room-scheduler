package com.company.meeting.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBiz(BizException ex) {
        return ResponseEntity.status(ex.status()).body(new ApiResponse<>(false, Map.of("code", ex.code()), ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuth() {
        return ResponseEntity.status(401).body(new ApiResponse<>(false, Map.of("code", "AUTH_FAILED"), "账号或密码错误"));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleDenied() {
        return ResponseEntity.status(403).body(new ApiResponse<>(false, Map.of("code", "FORBIDDEN"), "没有权限执行此操作"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + "：" + error.getDefaultMessage()).orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, Map.of("code", "VALIDATION_ERROR"), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("请求处理失败 {}",request.getRequestURI(),ex);
        return ResponseEntity.internalServerError().body(new ApiResponse<>(false, Map.of("code", "INTERNAL_ERROR", "path", request.getRequestURI()), "服务器处理失败"));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Object>> handleMalformed() {
        return ResponseEntity.badRequest().body(new ApiResponse<>(false,Map.of("code","INVALID_REQUEST"),"请求参数格式不合法"));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleIntegrity() {
        return ResponseEntity.status(409).body(new ApiResponse<>(false,Map.of("code","DATA_CONFLICT"),"数据已存在或关联资源不合法"));
    }
}
