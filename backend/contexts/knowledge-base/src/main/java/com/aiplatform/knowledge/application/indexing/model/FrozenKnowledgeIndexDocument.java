package com.aiplatform.knowledge.application.indexing.model;

import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildMember;
import java.util.Objects;

/**
 * generation 创建期间成对冻结的文档事实和成员快照。
 */
public record FrozenKnowledgeIndexDocument(
        KnowledgeDocument document,
        KnowledgeIndexRebuildMember snapshot
) {
    public FrozenKnowledgeIndexDocument {
        Objects.requireNonNull(document, "冻结文档不能为空");
        Objects.requireNonNull(snapshot, "冻结成员快照不能为空");
    }
}
