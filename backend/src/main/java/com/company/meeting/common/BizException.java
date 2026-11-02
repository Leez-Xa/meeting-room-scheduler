package com.company.meeting.common;

import org.springframework.http.HttpStatus;

/**
 * 携带稳定错误码、HTTP 状态及可选结构化详情的业务异常。
 * 继承 RuntimeException，使默认 Spring 事务规则下的业务失败触发回滚。
 * 结构化详情用于冲突日期等结果，前端无需解析中文提示。
 */
public class BizException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final Object details;

    /** 常见错误码映射为 404、409 或默认 400，特殊场景使用显式状态重载。 */
    public BizException(String code, String message) {
        this(code, message, code.equals("NOT_FOUND") ? HttpStatus.NOT_FOUND
                : java.util.Set.of("ROOM_CONFLICT","RECURRING_CONFLICT","RESOURCE_IN_USE","INVALID_STATUS","RESOURCE_CHANGED").contains(code) ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST);
    }

    /** 显式指定 HTTP 状态，例如 401、403 或数据库升级所需的 503。 */
    public BizException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    /** 完整错误契约，details 由异常处理器放入响应 data.details。 */
    public BizException(String code, String message, HttpStatus status, Object details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details;
    }

    /**
     * 返回供前端分支处理的稳定错误码。
     */
    public String code() { return code; }
    /**
     * 返回异常处理器采用的 HTTP 状态。
     */
    public HttpStatus status() { return status; }
    /**
     * 可选结构化详情，例如冲突日期或最新人员提示。
     */
    public Object details() { return details; }
}
