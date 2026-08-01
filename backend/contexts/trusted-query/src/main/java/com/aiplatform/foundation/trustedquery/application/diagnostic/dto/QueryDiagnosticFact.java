package com.aiplatform.foundation.trustedquery.application.diagnostic.dto;

import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunStatus;
import java.util.Objects;

/**
 * 查询终态同事务写入的最小内部诊断事实。
 *
 * <p>不保存问题、回答、候选正文、资源标识、权限集合或外部失败详情。</p>
 */
public record QueryDiagnosticFact(
        CandidateCollection candidateCollection,
        Integer candidateCount,
        RetrievalAuthorization retrievalAuthorization,
        long chatModelRevision,
        long embeddingModelRevision,
        CitationValidation citationValidation) {

    public QueryDiagnosticFact {
        candidateCollection = Objects.requireNonNull(candidateCollection, "候选检索事实不能为空");
        if (chatModelRevision < 1 || embeddingModelRevision < 1) {
            throw new IllegalArgumentException("查询诊断事实不正确");
        }
        if ((candidateCollection == CandidateCollection.COLLECTED)
                != (candidateCount != null)) {
            throw new IllegalArgumentException("候选数量与收集状态不一致");
        }
        if (candidateCount != null && candidateCount < 0) {
            throw new IllegalArgumentException("候选数量不能为负数");
        }
        retrievalAuthorization = Objects.requireNonNull(retrievalAuthorization, "检索授权结论不能为空");
        citationValidation = Objects.requireNonNull(citationValidation, "引用核验结论不能为空");
    }

    /**
     * 根据查询终态建立不允许猜测的引用核验结论。
     */
    public static QueryDiagnosticFact forTerminal(
            QueryRunStatus status,
            CandidateCollection candidateCollection,
            Integer candidateCount,
            long chatModelRevision,
            long embeddingModelRevision,
            CitationValidation citationValidation) {
        Objects.requireNonNull(status, "查询终态不能为空");
        if ((status == QueryRunStatus.ANSWERED) != (citationValidation == CitationValidation.VALIDATED)) {
            throw new IllegalArgumentException("查询终态与引用核验结论不一致");
        }
        if (status != QueryRunStatus.ANSWERED && citationValidation != CitationValidation.NOT_APPLICABLE) {
            throw new IllegalArgumentException("非回答查询的引用核验必须不可适用");
        }
        return new QueryDiagnosticFact(
                candidateCollection, candidateCount, RetrievalAuthorization.AUTHORIZED,
                chatModelRevision, embeddingModelRevision, citationValidation);
    }
}
