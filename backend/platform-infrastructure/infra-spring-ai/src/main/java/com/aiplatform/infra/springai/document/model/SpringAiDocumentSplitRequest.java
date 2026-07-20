package com.aiplatform.infra.springai.document.model;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.document.Document;

/**
 * Spring AI 文档切分请求，允许调用方按策略名称选择不同切分算法。
 */
public record SpringAiDocumentSplitRequest(
        String splitterName,
        List<Document> documents
) {
    public SpringAiDocumentSplitRequest {
        splitterName = requireText(splitterName, "splitterName");
        documents = copyDocuments(documents);
    }

    private static List<Document> copyDocuments(List<Document> documents) {
        Objects.requireNonNull(documents, "待切分文档不能为空");
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("待切分文档不能为空");
        }
        return List.copyOf(documents);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }
}
