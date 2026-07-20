package com.aiplatform.appstudio.domain;

import com.aiplatform.shared.kernel.ValueObject;

/**
 * AI 回答引用来源值对象，满足知识来源可追踪验收标准。
 */
public record Citation(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String documentName,
        String fragment,
        String location,
        double score
) implements ValueObject {
    public Citation {
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        documentId = requireText(documentId, "文档 ID");
        chunkId = requireText(chunkId, "切片 ID");
        documentName = requireText(documentName, "文档名称");
        fragment = requireText(fragment, "引用片段");
        location = requireText(location, "引用位置");
        if (!Double.isFinite(score) || score < 0 || score > 1) {
            throw new IllegalArgumentException("引用分数必须在 0 到 1 之间");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
