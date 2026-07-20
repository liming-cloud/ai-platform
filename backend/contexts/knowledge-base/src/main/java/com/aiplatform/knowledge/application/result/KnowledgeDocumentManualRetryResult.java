package com.aiplatform.knowledge.application.result;

import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import java.util.Objects;

/**
 * 用户显式重排失败解析作业后的持久化事实。
 */
public record KnowledgeDocumentManualRetryResult(KnowledgeDocument document, DocumentIngestJob job) {
    public KnowledgeDocumentManualRetryResult {
        Objects.requireNonNull(document, "重排文档不能为空");
        Objects.requireNonNull(job, "重排作业不能为空");
    }
}
