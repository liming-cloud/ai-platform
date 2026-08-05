package com.aiplatform.foundation.evaluationoperations.application.evaluation.request;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 私有 JSON Lines 中仅在单条执行期间存在的评测条目；不得持久化或投影。
 */
public record EvaluationDatasetEntry(
        String input,
        Set<String> expectedEvidenceStableIds,
        ExpectedOutcome expectedOutcome,
        Set<String> allowedAnswers) {

    private static final Pattern CHUNK_ID = Pattern.compile("[0-9a-f]{64}");

    public EvaluationDatasetEntry {
        if (input == null || input.isBlank() || !input.equals(input.trim())) {
            throw new IllegalArgumentException("评测输入格式不正确");
        }
        expectedEvidenceStableIds = Set.copyOf(Objects.requireNonNull(expectedEvidenceStableIds, "期望证据稳定标识不能为空"));
        expectedOutcome = Objects.requireNonNull(expectedOutcome, "预期结果不能为空");
        allowedAnswers = Set.copyOf(Objects.requireNonNull(allowedAnswers, "允许答案不能为空"));
        if (expectedEvidenceStableIds.stream().anyMatch(value -> value == null || !CHUNK_ID.matcher(value).matches())) {
            throw new IllegalArgumentException("期望证据稳定标识必须是分块标识");
        }
        if (allowedAnswers.stream().anyMatch(value -> value == null || value.isBlank() || !value.equals(value.trim()))) {
            throw new IllegalArgumentException("允许答案格式不正确");
        }
        if (expectedOutcome == ExpectedOutcome.ANSWER && expectedEvidenceStableIds.isEmpty()) {
            throw new IllegalArgumentException("ANSWER 条目必须包含期望证据");
        }
        if (expectedOutcome == ExpectedOutcome.REFUSE
                && (!expectedEvidenceStableIds.isEmpty() || !allowedAnswers.isEmpty())) {
            throw new IllegalArgumentException("REFUSE 条目不能包含证据或允许答案");
        }
    }

    /** 私有条目的期望结果类别。 */
    public enum ExpectedOutcome {
        ANSWER,
        REFUSE
    }
}
