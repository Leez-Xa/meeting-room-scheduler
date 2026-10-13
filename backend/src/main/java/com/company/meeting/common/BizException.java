package com.company.meeting.common;

import org.springframework.http.HttpStatus;

public class BizException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BizException(String code, String message) {
        this(code, message, code.equals("NOT_FOUND") ? HttpStatus.NOT_FOUND
                : java.util.Set.of("ROOM_CONFLICT","RESOURCE_IN_USE","INVALID_STATUS","RESOURCE_CHANGED").contains(code) ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST);
    }

    public BizException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
}
