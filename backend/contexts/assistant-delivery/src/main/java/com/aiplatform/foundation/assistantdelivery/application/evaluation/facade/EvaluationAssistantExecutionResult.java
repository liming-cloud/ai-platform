package com.aiplatform.foundation.assistantdelivery.application.evaluation.facade;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** 私有评测执行返回的无正文事实；失败不暴露底层异常。 */
public record EvaluationAssistantExecutionResult(
        Outcome outcome,
        Integer candidateCount,
        List<String> retrievedEvidenceStableIds,
        List<String> citedEvidenceStableIds) {

    private static final Pattern CHUNK_ID = Pattern.compile("[0-9a-f]{64}");

    public EvaluationAssistantExecutionResult {
        outcome = Objects.requireNonNull(outcome, "评测执行结论不能为空");
        if (candidateCount != null && candidateCount < 0) {
            throw new IllegalArgumentException("评测候选数量不能为负数");
        }
        retrievedEvidenceStableIds = requireChunkIds(retrievedEvidenceStableIds);
        citedEvidenceStableIds = requireChunkIds(citedEvidenceStableIds);
        if (outcome == Outcome.REFUSED_INSUFFICIENT_EVIDENCE && !citedEvidenceStableIds.isEmpty()) {
            throw new IllegalArgumentException("拒答不能携带引用稳定标识");
        }
        if (outcome != Outcome.FAILED
                && (candidateCount == null || candidateCount != retrievedEvidenceStableIds.size())) {
            throw new IllegalArgumentException("评测候选事实不正确");
        }
    }

    /** 无正文评测结论。 */
    public enum Outcome {
        ANSWERED,
        REFUSED_INSUFFICIENT_EVIDENCE,
        FAILED
    }

    private static List<String> requireChunkIds(List<String> values) {
        values = List.copyOf(Objects.requireNonNull(values, "评测证据稳定标识不能为空"));
        if (values.stream().anyMatch(value -> value == null || !CHUNK_ID.matcher(value).matches())) {
            throw new IllegalArgumentException("评测证据稳定标识必须是分块标识");
        }
        return values;
    }
}
