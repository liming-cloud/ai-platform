package com.aiplatform.knowledge.application.result;

import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeVisibility;

public record KnowledgeBaseDetailResult(
        String knowledgeBaseId,
        String name,
        String description,
        String visibility,
        String status,
        String embeddingModelConfigId
) {
    public KnowledgeBaseDetailResult {
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库ID");
        name = requireText(name, "知识库名称");
        description = description == null ? "" : description.trim();
        visibility = requireText(visibility, "知识库可见性");
        status = requireText(status, "知识库状态");
        embeddingModelConfigId = requireText(embeddingModelConfigId, "知识库向量模型配置 ID");
    }

    public static KnowledgeBaseDetailResult from(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseDetailResult(
                knowledgeBase.knowledgeBaseId(),
                knowledgeBase.name(),
                knowledgeBase.description(),
                toResponseVisibility(knowledgeBase.visibility()),
                knowledgeBase.published() ? "ACTIVE" : "DISABLED",
                knowledgeBase.embeddingModelConfigId()
        );
    }

    private static String toResponseVisibility(KnowledgeVisibility visibility) {
        return switch (visibility) {
            case PUBLIC -> "public";
            case DEPARTMENT -> "department";
            case PROJECT -> "project";
            case PRIVATE -> "private";
            case SPECIFIED_MEMBERS -> "members";
            case SPECIFIED_ROLES -> "roles";
        };
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
