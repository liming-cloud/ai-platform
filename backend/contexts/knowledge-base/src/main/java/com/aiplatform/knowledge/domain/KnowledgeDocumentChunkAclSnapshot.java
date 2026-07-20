package com.aiplatform.knowledge.domain;

import com.aiplatform.shared.kernel.ValueObject;
import java.util.List;
import java.util.Objects;

/**
 * 知识切片权限快照，生成 RAG 检索前置过滤和应用层 ACL 校验所需的强制权限标签。
 */
public record KnowledgeDocumentChunkAclSnapshot(List<String> tokens) implements ValueObject {
    public KnowledgeDocumentChunkAclSnapshot {
        Objects.requireNonNull(tokens, "权限快照不能为空");
        tokens = tokens.stream()
                .map(KnowledgeDocumentChunkAclSnapshot::requireAclToken)
                .distinct()
                .toList();
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("权限快照不能为空");
        }
    }

    public static KnowledgeDocumentChunkAclSnapshot forDocument(KnowledgeDocument document, String operatorId) {
        Objects.requireNonNull(document, "知识文档不能为空");
        String normalizedOperatorId = requireText(operatorId, "操作人ID");
        return new KnowledgeDocumentChunkAclSnapshot(List.of(
                "tenant:" + document.tenantId().value(),
                "kb:" + document.knowledgeBaseId(),
                "owner:" + normalizedOperatorId
        ));
    }

    private static String requireAclToken(String value) {
        return requireText(value, "权限快照");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }
}
