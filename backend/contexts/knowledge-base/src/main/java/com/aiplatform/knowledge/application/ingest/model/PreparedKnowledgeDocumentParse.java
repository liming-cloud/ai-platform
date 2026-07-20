package com.aiplatform.knowledge.application.ingest.model;

import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import java.util.List;
import java.util.Objects;

/**
 * 对象存储读取与解析完成后的不可变结果；此对象生成阶段不得持有数据库事务。
 */
public record PreparedKnowledgeDocumentParse(
        KnowledgeDocument sourceDocument,
        List<KnowledgeDocumentChunk> chunks
) {
    public PreparedKnowledgeDocumentParse {
        Objects.requireNonNull(sourceDocument, "源文档快照不能为空");
        Objects.requireNonNull(chunks, "解析切片不能为空");
        chunks = List.copyOf(chunks);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("解析切片不能为空");
        }
    }
}
