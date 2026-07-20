package com.aiplatform.contracts.capability.knowledge;

import java.util.List;

/**
 * 知识检索开放能力响应，显式区分可回答、授权引用、检索证据和无答案原因。
 */
public record KnowledgeRetrievalResponse(
        boolean answerable,
        List<KnowledgeRetrievalCitation> citations,
        List<KnowledgeRetrievalEvidence> evidences,
        String noAnswerReason,
        int filteredCitationCount
) {
    public KnowledgeRetrievalResponse(
            boolean answerable,
            List<KnowledgeRetrievalCitation> citations,
            List<KnowledgeRetrievalEvidence> evidences,
            String noAnswerReason
    ) {
        this(answerable, citations, evidences, noAnswerReason, 0);
    }

    public KnowledgeRetrievalResponse {
        citations = KnowledgeRetrievalContracts.copyRequiredList(citations, "引用列表");
        evidences = KnowledgeRetrievalContracts.copyRequiredList(evidences, "召回证据");
        if (filteredCitationCount < 0) {
            throw new IllegalArgumentException("过滤引用数量不能小于 0");
        }
        // 兼容保留字段，但禁止跨上下文暴露 ACL 过滤规模，内部计数仅供安全审计。
        filteredCitationCount = 0;
        if (answerable) {
            if (citations.isEmpty()) {
                throw new IllegalArgumentException("可回答结果必须携带引用列表");
            }
            if (evidences.isEmpty()) {
                throw new IllegalArgumentException("可回答结果必须携带召回证据");
            }
            if (noAnswerReason != null && !noAnswerReason.isBlank()) {
                throw new IllegalArgumentException("可回答结果不能携带无答案原因");
            }
            noAnswerReason = null;
        } else {
            noAnswerReason = KnowledgeRetrievalContracts.requireText(noAnswerReason, "无答案原因");
        }
    }
}
