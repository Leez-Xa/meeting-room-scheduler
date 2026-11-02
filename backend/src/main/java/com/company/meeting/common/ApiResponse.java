package com.company.meeting.common;

/**
 * 普通 JSON API 的统一响应外壳。
 * success 表示结果，data 承载数据或错误码，message 为提示；HTTP 状态另行设置。
 * CSV 下载返回文件内容，不使用此响应外壳。
 */
public record ApiResponse<T>(boolean success, T data, String message) {
    /**
     * 构造成功外壳，不在此决定权限或 HTTP 状态。
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "ok");
    }

    /**
     * 构造成功外壳，不在此决定权限或 HTTP 状态。
     */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }
}
