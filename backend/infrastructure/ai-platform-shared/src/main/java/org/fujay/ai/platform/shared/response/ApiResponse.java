package org.fujay.ai.platform.shared.response;

public record ApiResponse<T>(String code, String message, String traceId, T data) {

    public static <T> ApiResponse<T> success(String traceId, T data) {
        return new ApiResponse<>("0", "成功", traceId, data);
    }

    public static <T> ApiResponse<T> failure(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, traceId, null);
    }
}
