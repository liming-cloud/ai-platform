package com.aiplatform.appstudio.presentation.request;

import jakarta.validation.constraints.Size;

/**
 * 发送消息请求体，承接用户问题与链路追踪信息。
 */
public record SendChatMessageRequest(
        @Size(max = 128) String clientMessageId,
        @Size(max = 20000) String content,
        @Size(max = 20000) String message,
        @Size(max = 64) String traceId
) {
    public SendChatMessageRequest(String message, String traceId) {
        this(null, null, message, traceId);
    }

    public SendChatMessageRequest {
        clientMessageId = normalize(clientMessageId);
        content = normalize(content);
        message = normalize(message);
        traceId = normalizeTraceId(traceId);
        if (content == null && message == null) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
    }

    public String effectiveContent() {
        return content == null ? message : content;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeTraceId(String value) {
        String normalized = normalize(value);
        if (normalized != null && normalized.length() > 64) {
            throw new IllegalArgumentException("Trace ID 长度不能超过 64");
        }
        return normalized;
    }
}
