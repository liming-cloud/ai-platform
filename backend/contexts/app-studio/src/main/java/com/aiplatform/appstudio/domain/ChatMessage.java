package com.aiplatform.appstudio.domain;

import com.aiplatform.appstudio.domain.port.AppStudioPortContracts;
import com.aiplatform.shared.kernel.DomainEntity;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;

/**
 * AI 应用聊天消息实体，保存角色、正文、引用来源和创建时间。
 */
public record ChatMessage(
        TenantId tenantId,
        String messageId,
        String sessionId,
        ChatRole role,
        String content,
        List<Citation> citations,
        String modelInvocationLogId,
        String clientMessageId,
        String traceId,
        Instant createdAt
) implements DomainEntity<String> {
    public ChatMessage(
            TenantId tenantId,
            String messageId,
            String sessionId,
            ChatRole role,
            String content,
            List<Citation> citations,
            Instant createdAt
    ) {
        this(tenantId, messageId, sessionId, role, content, citations, null, null, null, createdAt);
    }

    public ChatMessage(
            TenantId tenantId,
            String messageId,
            String sessionId,
            ChatRole role,
            String content,
            List<Citation> citations,
            String modelInvocationLogId,
            Instant createdAt
    ) {
        this(tenantId, messageId, sessionId, role, content, citations, modelInvocationLogId, null, null, createdAt);
    }

    public ChatMessage(
            TenantId tenantId,
            String messageId,
            String sessionId,
            ChatRole role,
            String content,
            List<Citation> citations,
            String modelInvocationLogId,
            String traceId,
            Instant createdAt
    ) {
        this(tenantId, messageId, sessionId, role, content, citations, modelInvocationLogId, null, traceId, createdAt);
    }

    public ChatMessage {
        tenantId = requireValue(tenantId, "租户 ID");
        messageId = requireText(messageId, "消息 ID");
        sessionId = requireText(sessionId, "会话 ID");
        role = requireValue(role, "消息角色");
        content = requireText(content, "消息内容");
        citations = AppStudioPortContracts.copyRequiredList(citations, "引用来源");
        modelInvocationLogId = normalizeOptionalText(modelInvocationLogId);
        clientMessageId = normalizeOptionalText(clientMessageId);
        traceId = normalizeOptionalText(traceId);
        createdAt = requireValue(createdAt, "创建时间");
    }

    @Override
    public String id() {
        return messageId;
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
