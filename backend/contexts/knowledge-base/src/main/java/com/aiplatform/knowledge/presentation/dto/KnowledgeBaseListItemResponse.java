package com.aiplatform.knowledge.presentation.dto;

import com.aiplatform.knowledge.application.result.KnowledgeBaseListItemResult;

/**
 * 知识库列表 HTTP 响应项，用于前端知识库选择器。
 */
public record KnowledgeBaseListItemResponse(
        String knowledgeBaseId,
        String name,
        String visibility,
        String status,
        String embeddingModelConfigId
) {
    public static KnowledgeBaseListItemResponse from(KnowledgeBaseListItemResult result) {
        return new KnowledgeBaseListItemResponse(
                result.knowledgeBaseId(),
                result.name(),
                result.visibility(),
                result.status(),
                result.embeddingModelConfigId()
        );
    }
}
