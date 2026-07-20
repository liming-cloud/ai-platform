package com.aiplatform.knowledge.domain.port.answer.model;

import java.util.Objects;

/**
 * 答案生成端口的已授权证据模型，隔离领域端口与应用层检索结果 DTO。
 */
public record KnowledgeAnswerEvidence(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String content,
        String sourceUri,
        String titlePath,
        Integer paragraphNo,
        String tableCellRange,
        boolean authoritative
) {
    public KnowledgeAnswerEvidence {
        knowledgeBaseId = requireText(knowledgeBaseId, "knowledgeBaseId");
        documentId = requireText(documentId, "documentId");
        chunkId = requireText(chunkId, "chunkId");
        content = requireText(content, "content");
        sourceUri = requireText(sourceUri, "sourceUri");
        titlePath = requireText(titlePath, "titlePath");
        tableCellRange = tableCellRange == null ? null : normalizeOptionalText(tableCellRange, "tableCellRange");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value, String fieldName) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }
}
