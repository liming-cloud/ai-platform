package com.aiplatform.knowledge.domain.retrieval;

/**
 * 检索文档在租户、知识库和索引版本内的稳定身份，用于约束单文档上下文占比。
 */
public record RetrievalDocumentIdentity(
        String tenantId,
        String knowledgeBaseId,
        String documentId,
        String indexVersion
) {
    public RetrievalDocumentIdentity {
        tenantId = requireText(tenantId, "租户 ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        documentId = requireText(documentId, "文档 ID");
        indexVersion = requireText(indexVersion, "索引版本");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
