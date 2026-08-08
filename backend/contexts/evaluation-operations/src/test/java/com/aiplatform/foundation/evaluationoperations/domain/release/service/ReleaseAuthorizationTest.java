package com.aiplatform.foundation.evaluationoperations.domain.release.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunReleaseFact;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationTarget;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecision;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionId;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionReasonCode;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReleaseAuthorizationTest {

    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);
    private static final TenantId OTHER_TENANT_ID = new TenantId(100000000000000002L);
    private static final EvaluationTarget TARGET = new EvaluationTarget(100000000000000030L, 7L);
    private static final Instant DECIDED_AT = Instant.parse("2026-08-05T08:10:00Z");

    @Test
    void 仅完全匹配的通过运行决定可以授权发布() {
        var decision = ReleaseDecision.decide(
                new ReleaseDecisionId(100000000000000040L),
                passedFact(TENANT_ID, TARGET),
                DECIDED_AT);

        var authorization = ReleaseAuthorization.authorize(TENANT_ID, TARGET, decision, passedFact(TENANT_ID, TARGET));

        assertTrue(authorization.allowed());
        assertTrue(decision.policyVersion().equals("quality-gate-v1"));
        assertTrue(decision.reasonCode() == ReleaseDecisionReasonCode.QUALITY_GATE_PASSED);
    }

    @Test
    void 拒绝运行跨租户或目标修订不匹配均不得授权发布() {
        var rejected = ReleaseDecision.decide(
                new ReleaseDecisionId(100000000000000041L),
                new EvaluationRunReleaseFact(
                        TENANT_ID,
                        new EvaluationRunId(100000000000000021L),
                        TARGET,
                        "quality-gate-v1",
                        EvaluationRunStatus.FAILED),
                DECIDED_AT);
        var approved = ReleaseDecision.decide(
                new ReleaseDecisionId(100000000000000042L),
                passedFact(TENANT_ID, TARGET),
                DECIDED_AT);

        assertFalse(ReleaseAuthorization.authorize(
                TENANT_ID, TARGET, rejected,
                new EvaluationRunReleaseFact(
                        TENANT_ID,
                        new EvaluationRunId(100000000000000021L),
                        TARGET,
                        "quality-gate-v1",
                        EvaluationRunStatus.FAILED)).allowed());
        assertFalse(ReleaseAuthorization.authorize(
                OTHER_TENANT_ID, TARGET, approved, passedFact(TENANT_ID, TARGET)).allowed());
        assertFalse(ReleaseAuthorization.authorize(
                TENANT_ID, new EvaluationTarget(TARGET.assistantVersionId(), TARGET.assistantVersionRevision() + 1L),
                approved, passedFact(TENANT_ID, TARGET)).allowed());
        assertFalse(ReleaseAuthorization.authorize(
                TENANT_ID, TARGET, approved, passedFact(TENANT_ID, TARGET, "quality-gate-v2")).allowed());
    }

    private static EvaluationRunReleaseFact passedFact(TenantId tenantId, EvaluationTarget target) {
        return passedFact(tenantId, target, "quality-gate-v1");
    }

    private static EvaluationRunReleaseFact passedFact(
            TenantId tenantId, EvaluationTarget target, String policyVersion) {
        return new EvaluationRunReleaseFact(
                tenantId,
                new EvaluationRunId(100000000000000020L),
                target,
                policyVersion,
                EvaluationRunStatus.PASSED);
    }
}
