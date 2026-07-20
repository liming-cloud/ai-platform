package com.aiplatform.knowledge.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

public record CancelKnowledgeDocumentCommand(
        TenantId tenantId,
        String operatorId,
        String knowledgeBaseId,
        String documentId,
        String reason,
        String traceId
) {
    public CancelKnowledgeDocumentCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        operatorId = requireText(operatorId, "操作人 ID", 64);
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID", 64);
        documentId = requireText(documentId, "文档 ID", 64);
        reason = requireText(reason, "取消理由", 300);
        if (reason.codePointCount(0, reason.length()) < 3) {
            throw new IllegalArgumentException("取消理由至少需要 3 个字符");
        }
        traceId = requireText(traceId, "链路追踪 ID", 64);
    }

    private static String requireText(String value, String fieldName, int maxCodePoints) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxCodePoints);
        }
        return normalized;
    }
}
