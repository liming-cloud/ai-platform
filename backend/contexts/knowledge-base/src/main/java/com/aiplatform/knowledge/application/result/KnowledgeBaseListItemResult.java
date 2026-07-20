package com.aiplatform.knowledge.application.result;

import com.aiplatform.knowledge.domain.port.KnowledgeBaseListItemProjection;

/**
 * 知识库列表项应用层结果，供前端选择器展示租户内可用知识库。
 */
public record KnowledgeBaseListItemResult(
        String knowledgeBaseId,
        String name,
        String visibility,
        String status,
        String embeddingModelConfigId
) {
    public static KnowledgeBaseListItemResult from(KnowledgeBaseListItemProjection projection) {
        return new KnowledgeBaseListItemResult(
                projection.knowledgeBaseId(),
                projection.name(),
                projection.visibility(),
                projection.status(),
                projection.embeddingModelConfigId()
        );
    }
}
