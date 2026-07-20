package com.aiplatform.shared.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.aiplatform.shared.observability.TraceContext;

public final class ApiResponse<T> {
    public static final String SUCCESS_CODE = "SUCCESS";
    private final ResponseHeader header;
    private final T body;

    public ApiResponse(ResponseHeader header, T body) {
        this.header = header;
        this.body = body;
    }

    public static <T> ApiResponse<T> success(T body) {
        return new ApiResponse<>(new ResponseHeader(SUCCESS_CODE, "success", TraceContext.currentTraceId()), body);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return failure(code, message, null);
    }

    public static <T> ApiResponse<T> failure(String code, String message, T body) {
        return new ApiResponse<>(new ResponseHeader(code, message, TraceContext.currentTraceId()), body);
    }

    @JsonProperty("header")
    public ResponseHeader header() {
        return header;
    }

    @JsonProperty("body")
    public T body() {
        return body;
    }
}
