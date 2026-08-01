package com.aiplatform.foundation.trustedquery.application.diagnostic.dto;

import java.util.Objects;

/**
 * 仅供审计权限使用的最小查询诊断投影，不包含候选原文或模型标识。
 */
public record QueryDiagnosticView(
        String queryRunId,
        String status,
        CandidateCollection candidateCollection,
        Integer candidateCount,
        RetrievalAuthorization retrievalAuthorization,
        long chatModelRevision,
        long embeddingModelRevision,
        CitationValidation citationValidation,
        int citationCount,
        int inputTokens,
        int outputTokens,
        int totalTokens) {

    public QueryDiagnosticView {
        if (queryRunId == null || queryRunId.isBlank()) {
            throw new IllegalArgumentException("查询运行标识不能为空");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("查询状态不能为空");
        }
        Objects.requireNonNull(candidateCollection, "候选收集状态不能为空");
        Objects.requireNonNull(retrievalAuthorization, "检索授权状态不能为空");
        Objects.requireNonNull(citationValidation, "引用校验状态不能为空");
        if (chatModelRevision < 1 || embeddingModelRevision < 1
                || citationCount < 0 || inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("查询诊断数值不正确");
        }
    }
}
