package com.aiplatform.knowledge.application.indexing.model;

import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildPlan;

/**
 * 索引 generation 调度结果。
 */
public record KnowledgeIndexRebuildResult(
        KnowledgeIndexRebuildPlan plan,
        String requestedDocumentJobId,
        int scheduledJobCount,
        boolean reused
) {
    public KnowledgeIndexRebuildResult {
        requestedDocumentJobId = requestedDocumentJobId == null ? "" : requestedDocumentJobId.trim();
        if (scheduledJobCount < 0) {
            throw new IllegalArgumentException("调度任务数不能为负数");
        }
    }

    public static KnowledgeIndexRebuildResult empty() {
        return new KnowledgeIndexRebuildResult(null, "", 0, false);
    }

    public boolean hasPlan() {
        return plan != null;
    }
}
