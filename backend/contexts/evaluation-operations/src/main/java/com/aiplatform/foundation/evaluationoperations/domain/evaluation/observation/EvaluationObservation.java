package com.aiplatform.foundation.evaluationoperations.domain.evaluation.observation;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/**
 * 私有正文执行后产生的最小计数观察，不承载题目、回答、候选或引用正文。
 */
public record EvaluationObservation(
        TenantId tenantId,
        int expectedEvidenceCount,
        int retrievedExpectedEvidenceCount,
        int citationCount,
        int correctCitationCount,
        int expectedRefusalCount,
        int correctRefusalCount,
        int maximumRetrievedCandidateCount,
        int failedEntryCount,
        boolean unauthorizedEvidence) implements ValueObject {

    public EvaluationObservation {
        tenantId = Objects.requireNonNull(tenantId, "评测观察租户不能为空");
        requireNonNegative(expectedEvidenceCount, "预期证据计数");
        requireWithin(retrievedExpectedEvidenceCount, expectedEvidenceCount, "命中预期证据计数");
        requireNonNegative(citationCount, "引用计数");
        requireWithin(correctCitationCount, citationCount, "正确引用计数");
        requireNonNegative(expectedRefusalCount, "预期拒答计数");
        requireWithin(correctRefusalCount, expectedRefusalCount, "正确拒答计数");
        requireNonNegative(maximumRetrievedCandidateCount, "候选计数");
        requireNonNegative(failedEntryCount, "失败条目计数");
    }

    private static void requireWithin(int value, int maximum, String label) {
        requireNonNegative(value, label);
        if (value > maximum) {
            throw new IllegalArgumentException(label + "不能超过对应总数");
        }
    }

    private static void requireNonNegative(int value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + "不能为负数");
        }
    }
}
