package com.aiplatform.approval.support;

import com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope;
import java.util.Map;

/** Deterministic evidence values shared by approval-center contract tests. */
public final class ApprovalEvidenceTestFixtures {
    private ApprovalEvidenceTestFixtures() {
    }

    public static ApprovalEvidenceEnvelope evidence(String resourceId) {
        return ApprovalEvidenceEnvelope.create(Map.of(
                "target", Map.of("resourceId", resourceId),
                "changeType", "test"
        ));
    }

    public static String targetDigest(String resourceId) {
        return ApprovalEvidenceEnvelope.digestFacts(Map.of(
                "target", Map.of("resourceId", resourceId)
        ));
    }
}
