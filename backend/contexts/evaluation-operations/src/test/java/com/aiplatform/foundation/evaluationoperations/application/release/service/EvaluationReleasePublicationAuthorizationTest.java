package com.aiplatform.foundation.evaluationoperations.application.release.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.foundation.assistantdelivery.application.release.facade.ReleasePublicationRequest;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunReleaseFact;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationTarget;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunReleaseFactLookup;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecision;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionId;
import com.aiplatform.foundation.evaluationoperations.domain.release.repository.ReleaseDecisionRepository;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EvaluationReleasePublicationAuthorizationTest {

    private static final TenantId TENANT = new TenantId(8001L);
    private static final EvaluationRunId RUN_ID = new EvaluationRunId(9001L);
    private static final EvaluationTarget TARGET = new EvaluationTarget(9101L, 7L);

    @Test
    void 只允许完全匹配冻结决定运行和策略版本的发布() {
        var run = passedRun("quality-gate-v1");
        var decision = ReleaseDecision.decide(new ReleaseDecisionId(9201L), run, Instant.parse("2026-08-05T09:00:00Z"));
        var authorization = new EvaluationReleasePublicationAuthorization(
                new Runs(run), new Decisions(decision));

        assertThat(authorization.authorize(new ReleasePublicationRequest(
                TENANT, TARGET.assistantVersionId(), TARGET.assistantVersionRevision(), 9201L)).allowed()).isTrue();
        assertThat(new EvaluationReleasePublicationAuthorization(
                new Runs(passedRun("quality-gate-v2")), new Decisions(decision))
                .authorize(new ReleasePublicationRequest(
                        TENANT, TARGET.assistantVersionId(), TARGET.assistantVersionRevision(), 9201L)).allowed()).isFalse();
        assertThat(new EvaluationReleasePublicationAuthorization(new Runs(run), new Decisions(null))
                .authorize(new ReleasePublicationRequest(
                        TENANT, TARGET.assistantVersionId(), TARGET.assistantVersionRevision(), 9201L)).allowed()).isFalse();
    }

    private static EvaluationRunReleaseFact passedRun(String policyVersion) {
        return new EvaluationRunReleaseFact(TENANT, RUN_ID, TARGET, policyVersion, EvaluationRunStatus.PASSED);
    }

    private record Runs(EvaluationRunReleaseFact run) implements EvaluationRunReleaseFactLookup {
        @Override
        public Optional<EvaluationRunReleaseFact> findById(TenantId tenantId, EvaluationRunId id) {
            return Optional.ofNullable(run);
        }
    }

    private record Decisions(ReleaseDecision decision) implements ReleaseDecisionRepository {
        @Override
        public void save(ReleaseDecision value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ReleaseDecision> findById(TenantId tenantId, ReleaseDecisionId id) {
            return Optional.ofNullable(decision);
        }
    }
}
