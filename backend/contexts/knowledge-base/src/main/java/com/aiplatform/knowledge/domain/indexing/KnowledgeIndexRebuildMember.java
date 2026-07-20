package com.aiplatform.knowledge.domain.indexing;

import java.time.Instant;
import java.util.Objects;

/**
 * generation 创建时冻结的文档成员。之后到达的文档必须进入下一代。
 */
public record KnowledgeIndexRebuildMember(
        String documentId,
        Instant documentUpdatedAt,
        int expectedChunkCount
) {
    public KnowledgeIndexRebuildMember {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("文档 ID 不能为空");
        }
        documentId = documentId.trim();
        Objects.requireNonNull(documentUpdatedAt, "文档快照更新时间不能为空");
        if (expectedChunkCount < 1) {
            throw new IllegalArgumentException("成员切片数必须大于 0");
        }
    }
}
