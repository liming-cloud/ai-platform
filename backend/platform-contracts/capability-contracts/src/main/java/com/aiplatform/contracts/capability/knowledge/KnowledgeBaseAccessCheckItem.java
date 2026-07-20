package com.aiplatform.contracts.capability.knowledge;

public record KnowledgeBaseAccessCheckItem(
        String knowledgeBaseId,
        boolean accessible,
        String reasonCode
) {
    public KnowledgeBaseAccessCheckItem {
        knowledgeBaseId = KnowledgeRetrievalContracts.requireText(knowledgeBaseId, "知识库 ID");
        if (accessible) {
            reasonCode = null;
        } else {
            reasonCode = KnowledgeRetrievalContracts.requireText(reasonCode, "不可用原因编码");
        }
    }

    public static KnowledgeBaseAccessCheckItem allowed(String knowledgeBaseId) {
        return new KnowledgeBaseAccessCheckItem(knowledgeBaseId, true, null);
    }

    public static KnowledgeBaseAccessCheckItem denied(String knowledgeBaseId, String reasonCode) {
        return new KnowledgeBaseAccessCheckItem(knowledgeBaseId, false, reasonCode);
    }
}
