package com.aiplatform.knowledge.domain.retrieval;

/**
 * 检索证据的稳定身份。身份覆盖租户、知识库、文档、切片和索引版本，避免跨安全边界错误合并。
 */
public record RetrievalEvidenceIdentity(
        String tenantId,
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String indexVersion
) {
    public RetrievalEvidenceIdentity {
        tenantId = requireText(tenantId, "租户 ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        documentId = requireText(documentId, "文档 ID");
        chunkId = requireText(chunkId, "切片 ID");
        indexVersion = requireText(indexVersion, "索引版本");
    }

    public RetrievalDocumentIdentity documentIdentity() {
        return new RetrievalDocumentIdentity(tenantId, knowledgeBaseId, documentId, indexVersion);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
