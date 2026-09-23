package com.aiplatform.foundation.assistantdelivery.application.answer.facade;

import java.util.List;
import java.util.Objects;

/**
 * 可信回答执行的瞬时结果；调用方负责映射到各自运行根的终态。
 */
public record AssistantTrustedAnswerResult(
        Outcome outcome,
        String answer,
        List<AssistantTrustedAnswerCitation> citations,
        AssistantTrustedAnswerUsage usage,
        int candidateCount,
        long chatModelRevision,
        long embeddingModelRevision,
        ModelInvocationTrace modelInvocationTrace) {

    public AssistantTrustedAnswerResult(
            Outcome outcome,
            String answer,
            List<AssistantTrustedAnswerCitation> citations,
            AssistantTrustedAnswerUsage usage,
            int candidateCount,
            long chatModelRevision,
            long embeddingModelRevision) {
        this(
                outcome, answer, citations, usage, candidateCount, chatModelRevision,
                embeddingModelRevision, null);
    }

    /**
     * 不同运行根可以映射的可信执行结论。
     */
    public enum Outcome {
        ANSWERED,
        REFUSED_INSUFFICIENT_EVIDENCE
    }

    public AssistantTrustedAnswerResult {
        outcome = Objects.requireNonNull(outcome, "可信回答结论不能为空");
        citations = List.copyOf(Objects.requireNonNull(citations, "可信回答引用不能为空"));
        if (candidateCount < 0 || chatModelRevision < 1 || embeddingModelRevision < 1) {
            throw new IllegalArgumentException("可信回答执行事实不正确");
        }
        if (outcome == Outcome.ANSWERED) {
            if (answer == null || answer.isBlank() || citations.isEmpty() || usage == null) {
                throw new IllegalArgumentException("已回答结果必须包含正文、引用和用量");
            }
        } else if (answer != null || !citations.isEmpty() || usage != null) {
            throw new IllegalArgumentException("证据不足拒答不能携带正文、引用或用量");
        }
    }

    public static AssistantTrustedAnswerResult refused(
            int candidateCount, long chatModelRevision, long embeddingModelRevision) {
        return new AssistantTrustedAnswerResult(
                Outcome.REFUSED_INSUFFICIENT_EVIDENCE, null, List.of(), null,
                candidateCount, chatModelRevision, embeddingModelRevision, null);
    }
}
