package com.aiplatform.appstudio.application.result;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 发送消息后的应用层结果，返回回答、引用与召回证据。
 */
public record ChatMessageResult(
        String userMessageId,
        String assistantMessageId,
        String sessionId,
        String applicationId,
        String answer,
        boolean answerable,
        List<CitationResult> citations,
        List<RetrievalEvidenceResult> evidences,
        String noAnswerReason,
        int filteredCitationCount,
        Instant createdAt,
        Map<String, Object> metadata
) {
    public ChatMessageResult(
            String userMessageId,
            String assistantMessageId,
            String sessionId,
            String applicationId,
            String answer,
            boolean answerable,
            List<CitationResult> citations,
            List<RetrievalEvidenceResult> evidences,
            String noAnswerReason,
            Instant createdAt,
            Map<String, Object> metadata
    ) {
        this(userMessageId, assistantMessageId, sessionId, applicationId, answer, answerable,
                citations, evidences, noAnswerReason, 0, createdAt, metadata);
    }

    public ChatMessageResult {
        if (filteredCitationCount < 0) {
            throw new IllegalArgumentException("过滤引用数量不能小于 0");
        }
    }
}
