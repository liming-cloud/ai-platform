package com.aiplatform.foundation.assistantdelivery.api.model;

import com.aiplatform.foundation.assistantdelivery.domain.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.RefusalPolicy;
import java.util.Objects;
import java.util.UUID;

/**
 * 可信查询执行时冻结使用的已发布助手版本事实。
 */
public record PublishedAssistantVersionSnapshot(
        UUID assistantVersionId,
        UUID assistantId,
        int number,
        long chatDeploymentId,
        long chatDeploymentRevision,
        long knowledgeSpaceId,
        long indexVersionId,
        long indexVersionRevision,
        long embeddingDeploymentId,
        long embeddingDeploymentRevision,
        int embeddingDimension,
        String systemInstruction,
        int maxOutputTokens,
        int retrievalLimit,
        double minimumScore,
        CitationPolicy citationPolicy,
        RefusalPolicy refusalPolicy) {

    public PublishedAssistantVersionSnapshot {
        Objects.requireNonNull(assistantVersionId, "助手版本标识不能为空");
        Objects.requireNonNull(assistantId, "助手标识不能为空");
        if (knowledgeSpaceId <= 0 || indexVersionId <= 0) {
            throw new IllegalArgumentException("知识空间标识和索引版本标识必须是正整数");
        }
        Objects.requireNonNull(systemInstruction, "系统指令不能为空");
        Objects.requireNonNull(citationPolicy, "引用策略不能为空");
        Objects.requireNonNull(refusalPolicy, "拒答策略不能为空");
        if (chatDeploymentId <= 0
                || embeddingDeploymentId <= 0
                || number < 1
                || chatDeploymentRevision < 1
                || indexVersionRevision < 1
                || embeddingDeploymentRevision < 1
                || embeddingDimension < 1
                || maxOutputTokens < 1
                || retrievalLimit < 1
                || !Double.isFinite(minimumScore)
                || minimumScore < 0.0
                || minimumScore > 1.0) {
            throw new IllegalArgumentException("已发布助手版本快照不完整");
        }
        if (citationPolicy != CitationPolicy.REQUIRED
                || refusalPolicy != RefusalPolicy.INSUFFICIENT_EVIDENCE) {
            throw new IllegalArgumentException("已发布助手版本必须执行可信回答策略");
        }
    }
}
