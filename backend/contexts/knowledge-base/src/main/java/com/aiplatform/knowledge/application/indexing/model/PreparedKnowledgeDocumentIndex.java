package com.aiplatform.knowledge.application.indexing.model;

import com.aiplatform.knowledge.domain.DocumentIndexJobTarget;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import java.util.List;
import java.util.Objects;

/**
 * 外部向量化完成后的候选结果；短事务 finalizer 仍需校验文档快照、租约和 latest generation。
 */
public record PreparedKnowledgeDocumentIndex(
        KnowledgeDocument sourceDocument,
        DocumentIndexJobTarget target,
        List<ChunkEmbedding> embeddings
) {
    public PreparedKnowledgeDocumentIndex {
        Objects.requireNonNull(sourceDocument, "源文档快照不能为空");
        Objects.requireNonNull(target, "索引候选目标不能为空");
        Objects.requireNonNull(embeddings, "切片向量不能为空");
        embeddings = List.copyOf(embeddings);
        if (embeddings.isEmpty()) {
            throw new IllegalArgumentException("切片向量不能为空");
        }
    }
}
