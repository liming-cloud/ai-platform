package com.aiplatform.knowledge.presentation.dto;

import com.aiplatform.knowledge.application.result.KnowledgeBaseDetailResult;

public record KnowledgeBaseDetailResponse(
        String knowledgeBaseId,
        String name,
        String description,
        String visibility,
        String status,
        String embeddingModelConfigId
) {
    public KnowledgeBaseDetailResponse {
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库ID");
        name = requireText(name, "知识库名称");
        description = description == null ? "" : description.trim();
        visibility = requireText(visibility, "知识库可见性");
        status = requireText(status, "知识库状态");
        embeddingModelConfigId = requireText(embeddingModelConfigId, "知识库向量模型配置 ID");
    }

    public static KnowledgeBaseDetailResponse from(KnowledgeBaseDetailResult result) {
        return new KnowledgeBaseDetailResponse(
                result.knowledgeBaseId(),
                result.name(),
                result.description(),
                result.visibility(),
                result.status(),
                result.embeddingModelConfigId()
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
