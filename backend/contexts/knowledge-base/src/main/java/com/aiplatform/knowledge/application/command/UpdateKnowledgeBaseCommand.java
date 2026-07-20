package com.aiplatform.knowledge.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

public record UpdateKnowledgeBaseCommand(
        TenantId tenantId,
        String operatorId,
        String operatorType,
        String knowledgeBaseId,
        String name,
        String visibility,
        String description,
        String embeddingModelConfigId
) {
    public UpdateKnowledgeBaseCommand {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        operatorId = requireText(operatorId, "操作人ID");
        operatorType = requireText(operatorType, "操作人类型");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库ID");
        name = requireText(name, "知识库名称");
        visibility = requireText(visibility, "知识库可见性");
        description = description == null ? "" : description.trim();
        embeddingModelConfigId = requireText(embeddingModelConfigId, "知识库向量模型配置 ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
