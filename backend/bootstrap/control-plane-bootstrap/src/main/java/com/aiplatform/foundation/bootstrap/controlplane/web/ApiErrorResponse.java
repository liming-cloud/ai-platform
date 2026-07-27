package com.aiplatform.foundation.bootstrap.controlplane.web;

import java.util.Map;
import java.util.Objects;

/**
 * 控制面统一错误响应。
 */
public record ApiErrorResponse(
        String code,
        String message,
        String traceId,
        Map<String, Object> details) {

    public ApiErrorResponse {
        Objects.requireNonNull(code, "错误码不能为空");
        Objects.requireNonNull(message, "错误消息不能为空");
        Objects.requireNonNull(traceId, "追踪标识不能为空");
        details = Map.copyOf(Objects.requireNonNull(details, "错误详情不能为空"));
    }
}
