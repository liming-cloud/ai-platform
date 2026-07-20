package com.aiplatform.knowledge.application.result;

/**
 * 知识库创建成功后的应用层响应事实，供接口层返回新知识库标识和状态。
 */
public record CreateKnowledgeBaseResult(
        String knowledgeBaseId,
        String status
) {
    public CreateKnowledgeBaseResult {
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库ID");
        status = requireText(status, "知识库状态");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
