package com.aiplatform.appstudio.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;
import java.util.UUID;

/**
 * 发送聊天消息命令，要求应用、会话与当前主体三者同时成立。
 */
public record SendChatMessageCommand(
        TenantId tenantId,
        String actorId,
        String sessionId,
        String clientMessageId,
        String message,
        String traceId
) {
    public SendChatMessageCommand(
            TenantId tenantId,
            String actorId,
            String sessionId,
            String message,
            String traceId
    ) {
        this(tenantId, actorId, sessionId, defaultClientMessageId(traceId), message, traceId);
    }

    public SendChatMessageCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = requireText(actorId, "操作者 ID");
        sessionId = requireText(sessionId, "会话 ID");
        clientMessageId = requireText(clientMessageId, "客户端消息 ID");
        message = requireText(message, "消息内容");
        traceId = normalizeOptionalText(traceId);
        if (traceId.length() > 64) {
            throw new IllegalArgumentException("Trace ID 长度不能超过 64");
        }
    }

    private static String defaultClientMessageId(String traceId) {
        return traceId == null || traceId.isBlank() ? "legacy-" + UUID.randomUUID() : traceId.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null ? "" : value.trim();
    }
}
