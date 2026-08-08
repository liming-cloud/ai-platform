package com.aiplatform.foundation.evaluationoperations.application.release.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunReleaseFact;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationTarget;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunReleaseFactLookup;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecision;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionReasonCode;
import com.aiplatform.foundation.evaluationoperations.domain.release.repository.ReleaseDecisionRepository;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReleaseDecisionServiceTest {

    private static final TenantId TENANT = new TenantId(8001L);
    private static final EvaluationRunId RUN_ID = new EvaluationRunId(9001L);
    private static final EvaluationTarget TARGET = new EvaluationTarget(9101L, 7L);

    @Test
    void 只有同租户通过运行可形成不可变授权决定() {
        var context = mock(TrustedRequestContext.class);
        when(context.tenantId()).thenReturn(TENANT);
        when(context.hasPermission(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var run = new EvaluationRunReleaseFact(TENANT, RUN_ID, TARGET, "quality-gate-v1", EvaluationRunStatus.PASSED);
        var decisions = new CapturingDecisions();
        var service = new ReleaseDecisionService(
                new Runs(run), decisions, () -> 9201L,
                Clock.fixed(Instant.parse("2026-08-05T09:00:00Z"), ZoneOffset.UTC));

        var decision = service.decide(context, RUN_ID.value());

        assertThat(decision.status().name()).isEqualTo("AUTHORIZED");
        assertThat(decision.policyVersion()).isEqualTo("quality-gate-v1");
        assertThat(decision.reasonCode()).isEqualTo(ReleaseDecisionReasonCode.QUALITY_GATE_PASSED);
        assertThat(decisions.saved).isSameAs(decision);
    }

    @Test
    void 跨租户运行或缺少助手写权限均不得形成决定() {
        var denied = mock(TrustedRequestContext.class);
        when(denied.hasPermission(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        var service = new ReleaseDecisionService(
                new Runs(null), new CapturingDecisions(), () -> 9201L, Clock.systemUTC());

        assertThatThrownBy(() -> service.decide(denied, RUN_ID.value()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class CapturingDecisions implements ReleaseDecisionRepository {
        private ReleaseDecision saved;
        @Override public void save(ReleaseDecision decision) { saved = decision; }
        @Override public Optional<ReleaseDecision> findById(TenantId tenantId, com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionId id) { return Optional.empty(); }
    }

    private static final class Runs implements EvaluationRunReleaseFactLookup {
        private final EvaluationRunReleaseFact run;
        private Runs(EvaluationRunReleaseFact run) { this.run = run; }
        @Override public Optional<EvaluationRunReleaseFact> findById(TenantId tenantId, EvaluationRunId id) {
            return run == null ? Optional.empty() : Optional.of(run);
        }
    }
}
