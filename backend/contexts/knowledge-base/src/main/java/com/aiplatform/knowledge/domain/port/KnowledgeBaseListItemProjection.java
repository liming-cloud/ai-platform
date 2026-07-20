package com.aiplatform.knowledge.domain.port;

/**
 * 知识库列表投影，只承载选择器和列表展示需要的字段。
 */
public record KnowledgeBaseListItemProjection(
        String knowledgeBaseId,
        String name,
        String visibility,
        String status,
        String embeddingModelConfigId
) {
}
