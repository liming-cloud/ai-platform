package com.aiplatform.foundation.knowledgedata.application.knowledge.facade;

import java.math.BigInteger;
import java.util.Objects;

/** 当前已核实的配额下限；`complete=false` 时不能据此推断未知来源的用量为零。 */
public record KnowledgeQuotaUsageSnapshot(
        long knowledgeBases,
        long documentsPerKnowledgeBase,
        BigInteger bytesPerKnowledgeBase,
        boolean complete) {

    public KnowledgeQuotaUsageSnapshot {
        Objects.requireNonNull(bytesPerKnowledgeBase, "原始内容字节数不能为空");
        if (knowledgeBases < 0 || documentsPerKnowledgeBase < 0 || bytesPerKnowledgeBase.signum() < 0) {
            throw new IllegalArgumentException("已用量不能为负数");
        }
    }
}
