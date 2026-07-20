package com.aiplatform.knowledge.application.result;

/**
 * 文档解析完成后的应用层响应事实，供接口层返回入库任务状态和生成切片数量。
 */
public record ParseKnowledgeDocumentResult(
        String documentId,
        String ingestJobId,
        String status,
        int chunkCount
) {
    public ParseKnowledgeDocumentResult {
        documentId = requireText(documentId, "文档ID");
        ingestJobId = requireText(ingestJobId, "入库任务ID");
        status = requireText(status, "解析状态");
        if (chunkCount < 0) {
            throw new IllegalArgumentException("解析切片数量不能为负数");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
