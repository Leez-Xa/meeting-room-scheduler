package com.company.meeting.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 将控制器异常转换为统一响应及合适的 HTTP 状态。
 * 区分认证、授权、参数、业务冲突与内部错误，堆栈只写服务端日志。
 * 安全过滤链内的异常由 SecurityConfig 另行处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BizException.class)
    /**
     * 保留业务错误码和详情，使用异常指定的 HTTP 状态。
     */
    public ResponseEntity<ApiResponse<Object>> handleBiz(BizException ex) {
        Map<String,Object> data = new LinkedHashMap<>(); data.put("code", ex.code());
        if (ex.details() != null) data.put("details", ex.details());
        return ResponseEntity.status(ex.status()).body(new ApiResponse<>(false, data, ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    /**
     * 凭证错误统一 401，不分别暴露账号是否存在。
     */
    public ResponseEntity<ApiResponse<Object>> handleAuth() {
        return ResponseEntity.status(401).body(new ApiResponse<>(false, Map.of("code", "AUTH_FAILED"), "账号或密码错误"));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    /**
     * 方法授权失败返回 403，过滤链内异常另行处理。
     */
    public ResponseEntity<ApiResponse<Object>> handleDenied() {
        return ResponseEntity.status(403).body(new ApiResponse<>(false, Map.of("code", "FORBIDDEN"), "没有权限执行此操作"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    /**
     * 首个字段约束错误形成提示，返回 400。
     */
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + "：" + error.getDefaultMessage()).orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, Map.of("code", "VALIDATION_ERROR"), message));
    }

    @ExceptionHandler(Exception.class)
    /**
     * 堆栈写日志，前端只收到通用 500 提示与路径。
     */
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("请求处理失败 {}",request.getRequestURI(),ex);
        return ResponseEntity.internalServerError().body(new ApiResponse<>(false, Map.of("code", "INTERNAL_ERROR", "path", request.getRequestURI()), "服务器处理失败"));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,IllegalArgumentException.class})
    /**
     * JSON、枚举、日期和数值转换错误统一归为 400。
     */
    public ResponseEntity<ApiResponse<Object>> handleMalformed() {
        return ResponseEntity.badRequest().body(new ApiResponse<>(false,Map.of("code","INVALID_REQUEST"),"请求参数格式不合法"));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    /**
     * 唯一键或外键等完整性失败归 409，不泄露数据库文本。
     */
    public ResponseEntity<ApiResponse<Object>> handleIntegrity() {
        return ResponseEntity.status(409).body(new ApiResponse<>(false,Map.of("code","DATA_CONFLICT"),"数据已存在或关联资源不合法"));
    }
}
