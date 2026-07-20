package com.aiplatform.knowledge.infrastructure.messaging.converter;

record NormalizedDocumentIngestRequestedPayload(
        String ingestJobId,
        String knowledgeBaseId,
        String documentId
) {
    NormalizedDocumentIngestRequestedPayload {
        ingestJobId = requireText(ingestJobId, "入库任务 ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        documentId = requireText(documentId, "文档 ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
