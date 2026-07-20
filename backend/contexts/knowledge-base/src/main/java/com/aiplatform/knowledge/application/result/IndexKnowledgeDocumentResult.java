package com.aiplatform.knowledge.application.result;

/**
 * 文档向量化索引结果，失败时必须返回明确原因并保留任务证据。
 */
public record IndexKnowledgeDocumentResult(
        String documentId,
        String ingestJobId,
        String status,
        int indexedChunkCount,
        String failureReason
) {
    public IndexKnowledgeDocumentResult {
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
                throw new IllegalArgumentException("非失败索引结果不能携带失败原因");
            }
            failureReason = "";
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
