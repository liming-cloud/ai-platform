package com.aiplatform.knowledge.presentation.dto;

import com.aiplatform.knowledge.application.result.CreateKnowledgeBaseResult;

/**
 * 知识库创建 HTTP 响应，暴露已创建知识库的标识和业务状态。
 */
public record KnowledgeBaseResponse(
        String knowledgeBaseId,
        String status
) {
    public KnowledgeBaseResponse {
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库ID");
        status = requireText(status, "知识库状态");
    }

    public static KnowledgeBaseResponse from(CreateKnowledgeBaseResult result) {
        return new KnowledgeBaseResponse(result.knowledgeBaseId(), result.status());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
