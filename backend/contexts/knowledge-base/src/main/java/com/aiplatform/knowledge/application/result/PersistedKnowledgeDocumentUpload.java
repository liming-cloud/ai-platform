package com.aiplatform.knowledge.application.result;

import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import java.util.Objects;

/**
 * 对象上传后的数据库原子提交结果。
 */
public record PersistedKnowledgeDocumentUpload(KnowledgeDocument document, DocumentIngestJob job) {
    public PersistedKnowledgeDocumentUpload {
        Objects.requireNonNull(document, "已持久化文档不能为空");
        Objects.requireNonNull(job, "已持久化作业不能为空");
    }
}
