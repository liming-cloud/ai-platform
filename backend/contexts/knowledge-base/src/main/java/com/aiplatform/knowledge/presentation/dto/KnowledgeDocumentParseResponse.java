package com.aiplatform.knowledge.presentation.dto;

import com.aiplatform.knowledge.application.result.ParseKnowledgeDocumentResult;

/**
 * 知识文档解析 HTTP 响应，暴露入库任务状态和本次生成切片数量。
 */
public record KnowledgeDocumentParseResponse(
        String documentId,
        String ingestJobId,
        String status,
        int chunkCount
) {
    public KnowledgeDocumentParseResponse {
        documentId = requireText(documentId, "文档ID");
        ingestJobId = requireText(ingestJobId, "入库任务ID");
        status = requireText(status, "解析状态");
        if (chunkCount < 0) {
            throw new IllegalArgumentException("解析切片数量不能为负数");
        }
    }

    public static KnowledgeDocumentParseResponse from(ParseKnowledgeDocumentResult result) {
        return new KnowledgeDocumentParseResponse(
                result.documentId(),
                result.ingestJobId(),
                result.status(),
                result.chunkCount()
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
