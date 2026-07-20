package com.aiplatform.knowledge.presentation.dto;

import com.aiplatform.knowledge.application.result.UploadKnowledgeDocumentResult;

/**
 * 知识文档上传 HTTP 响应，暴露文档标识、入库任务和上传状态。
 */
public record KnowledgeDocumentResponse(
        String documentId,
        String ingestJobId,
        String status
) {
    public KnowledgeDocumentResponse {
        documentId = requireText(documentId, "文档ID");
        ingestJobId = requireText(ingestJobId, "入库任务ID");
        status = requireText(status, "上传状态");
    }

    public static KnowledgeDocumentResponse from(UploadKnowledgeDocumentResult result) {
        return new KnowledgeDocumentResponse(result.documentId(), result.ingestJobId(), result.status());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
