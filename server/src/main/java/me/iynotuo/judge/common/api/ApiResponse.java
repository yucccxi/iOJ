package me.iynotuo.judge.common.api;

/**
 * Unified response body. code == 0 means success; any other value is a business error code (see ErrorCode).
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static final int SUCCESS_CODE = 0;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(SUCCESS_CODE, "ok", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
