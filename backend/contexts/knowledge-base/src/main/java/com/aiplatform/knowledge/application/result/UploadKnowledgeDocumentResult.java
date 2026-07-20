package com.aiplatform.knowledge.application.result;

/**
 * 文档上传成功后的应用层响应事实，供接口层返回文档标识、入库任务和上传状态。
 */
public record UploadKnowledgeDocumentResult(
        String documentId,
        String ingestJobId,
        String status
) {
    public UploadKnowledgeDocumentResult {
        documentId = requireText(documentId, "文档ID");
        ingestJobId = requireText(ingestJobId, "入库任务ID");
        status = requireText(status, "上传状态");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
