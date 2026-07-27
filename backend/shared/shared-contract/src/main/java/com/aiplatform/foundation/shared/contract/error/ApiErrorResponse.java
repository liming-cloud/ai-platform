package com.aiplatform.foundation.shared.contract.error;

import java.util.Map;
import java.util.Objects;

/**
 * 公共接口统一错误响应。
 */
public record ApiErrorResponse(
        String code,
        String message,
        String traceId,
        Map<String, Object> details) {

    public ApiErrorResponse {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("错误编码不能为空");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("错误消息不能为空");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("追踪标识不能为空");
        }
        details = Map.copyOf(Objects.requireNonNull(details, "错误详情不能为空"));
    }
}
