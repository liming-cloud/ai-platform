package com.aiplatform.knowledge.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

public record DisableKnowledgeBaseCommand(
        TenantId tenantId,
        String operatorId,
        String knowledgeBaseId
) {
    public DisableKnowledgeBaseCommand {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        operatorId = requireText(operatorId, "操作人ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
