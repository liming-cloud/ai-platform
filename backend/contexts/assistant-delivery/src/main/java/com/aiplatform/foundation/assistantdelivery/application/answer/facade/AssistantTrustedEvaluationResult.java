package com.aiplatform.foundation.assistantdelivery.application.answer.facade;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 可信回答供私有评测使用的无正文瞬时结果。
 */
public record AssistantTrustedEvaluationResult(
        Outcome outcome,
        List<String> retrievedEvidenceStableIds,
        List<String> citedEvidenceStableIds,
        int candidateCount) {

    private static final Pattern CHUNK_ID = Pattern.compile("[0-9a-f]{64}");

    public AssistantTrustedEvaluationResult {
        outcome = Objects.requireNonNull(outcome, "评测可信回答结论不能为空");
        retrievedEvidenceStableIds = requireChunkIds(retrievedEvidenceStableIds);
        citedEvidenceStableIds = requireChunkIds(citedEvidenceStableIds);
        if (candidateCount < 0 || candidateCount != retrievedEvidenceStableIds.size()) {
            throw new IllegalArgumentException("评测候选事实不正确");
        }
        if (outcome == Outcome.REFUSED_INSUFFICIENT_EVIDENCE && !citedEvidenceStableIds.isEmpty()) {
            throw new IllegalArgumentException("拒答不能携带引用稳定标识");
        }
    }

    /** 评测可消费的无正文回答结论。 */
    public enum Outcome {
        ANSWERED,
        REFUSED_INSUFFICIENT_EVIDENCE
    }

    private static List<String> requireChunkIds(List<String> values) {
        values = List.copyOf(Objects.requireNonNull(values, "评测证据稳定标识不能为空"));
        if (values.stream().anyMatch(value -> value == null || !CHUNK_ID.matcher(value).matches())) {
            throw new IllegalArgumentException("评测证据稳定标识必须是分块标识");
        }
        return values;
    }
}
