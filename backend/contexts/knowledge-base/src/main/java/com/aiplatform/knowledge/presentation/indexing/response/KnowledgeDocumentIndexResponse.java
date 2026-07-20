package com.aiplatform.knowledge.presentation.indexing.response;

import com.aiplatform.knowledge.application.result.IndexKnowledgeDocumentResult;

/**
 * 文档索引响应 DTO，暴露任务状态和失败原因，便于前端查看索引进度。
 */
public record KnowledgeDocumentIndexResponse(
        String documentId,
        String ingestJobId,
        String status,
        int indexedChunkCount,
        String failureReason
) {
    public KnowledgeDocumentIndexResponse {
        documentId = requireText(documentId, "文档ID");
        ingestJobId = requireText(ingestJobId, "入库任务ID");
        status = requireText(status, "索引状态");
        if (indexedChunkCount < 0) {
            throw new IllegalArgumentException("索引切片数量不能为负数");
        }
        if ("FAILED".equals(status)) {
            failureReason = requireText(failureReason, "失败原因");
        } else if (failureReason == null) {
            failureReason = "";
        } else {
            String normalizedFailureReason = failureReason.trim();
            if (!normalizedFailureReason.isEmpty()) {
                throw new IllegalArgumentException("非失败索引响应不能携带失败原因");
            }
            failureReason = "";
        }
    }

    public static KnowledgeDocumentIndexResponse from(IndexKnowledgeDocumentResult result) {
        return new KnowledgeDocumentIndexResponse(
                result.documentId(),
                result.ingestJobId(),
                result.status(),
                result.indexedChunkCount(),
                result.failureReason()
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
