package com.aiplatform.knowledge.application.indexing.model;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 创建或复用索引 generation 的事务请求。
 */
public record KnowledgeIndexRebuildRequest(
        TenantId tenantId,
        String knowledgeBaseId,
        String operatorId,
        String operatorType,
        String targetModelConfigId,
        String targetEmbeddingModel,
        String requestedDocumentId
) {
    public KnowledgeIndexRebuildRequest {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        operatorId = requireText(operatorId, "操作人 ID");
        operatorType = requireText(operatorType, "操作人类型");
        targetModelConfigId = requireText(targetModelConfigId, "目标模型配置 ID");
        targetEmbeddingModel = requireText(targetEmbeddingModel, "目标 Embedding 模型");
        requestedDocumentId = requestedDocumentId == null ? "" : requestedDocumentId.trim();
    }

    public boolean hasRequestedDocument() {
        return !requestedDocumentId.isEmpty();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
