package com.aiplatform.foundation.evaluationoperations.domain.release.service;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunReleaseFact;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationTarget;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecision;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionStatus;
import com.aiplatform.foundation.evaluationoperations.domain.release.operation.ReleaseAuthorizationResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/**
 * 复核冻结发布决定与运行事实的一次性无状态领域服务。
 */
public final class ReleaseAuthorization {

    private ReleaseAuthorization() {
    }

    /**
     * 只有同租户、同目标修订且通过运行完全匹配的决定可授权发布。
     */
    public static ReleaseAuthorizationResult authorize(
            TenantId tenantId,
            EvaluationTarget target,
            ReleaseDecision decision,
            EvaluationRunReleaseFact run) {
        tenantId = Objects.requireNonNull(tenantId, "发布租户不能为空");
        target = Objects.requireNonNull(target, "发布目标不能为空");
        decision = Objects.requireNonNull(decision, "发布决定不能为空");
        run = Objects.requireNonNull(run, "评测运行发布事实不能为空");
        var allowed = decision.status() == ReleaseDecisionStatus.AUTHORIZED
                && run.status() == EvaluationRunStatus.PASSED
                && tenantId.equals(decision.tenantId())
                && tenantId.equals(run.tenantId())
                && target.equals(decision.target())
                && target.equals(run.target())
                && decision.evaluationRunId().equals(run.evaluationRunId());
        return new ReleaseAuthorizationResult(allowed);
    }
}
