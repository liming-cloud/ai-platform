package com.aiplatform.foundation.evaluationoperations.application.release.service;

import com.aiplatform.foundation.assistantdelivery.application.release.facade.ReleasePublicationAuthorization;
import com.aiplatform.foundation.assistantdelivery.application.release.facade.ReleasePublicationAuthorizationResult;
import com.aiplatform.foundation.assistantdelivery.application.release.facade.ReleasePublicationRequest;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationTarget;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunReleaseFactLookup;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionId;
import com.aiplatform.foundation.evaluationoperations.domain.release.repository.ReleaseDecisionRepository;
import com.aiplatform.foundation.evaluationoperations.domain.release.service.ReleaseAuthorization;
import java.util.Objects;

/**
 * 将助手发布请求收敛为冻结决定与运行事实的无状态复核；任何缺失事实均拒绝发布。
 */
public final class EvaluationReleasePublicationAuthorization implements ReleasePublicationAuthorization {

    private final EvaluationRunReleaseFactLookup runs;
    private final ReleaseDecisionRepository decisions;

    public EvaluationReleasePublicationAuthorization(
            EvaluationRunReleaseFactLookup runs, ReleaseDecisionRepository decisions) {
        this.runs = Objects.requireNonNull(runs, "评测运行事实读取边界不能为空");
        this.decisions = Objects.requireNonNull(decisions, "发布决定仓储不能为空");
    }

    @Override
    public ReleasePublicationAuthorizationResult authorize(ReleasePublicationRequest request) {
        request = Objects.requireNonNull(request, "发布授权请求不能为空");
        var decision = decisions.findById(request.tenantId(), new ReleaseDecisionId(request.releaseDecisionId()));
        if (decision.isEmpty()) {
            return new ReleasePublicationAuthorizationResult(false);
        }
        var run = runs.findById(request.tenantId(), decision.get().evaluationRunId());
        if (run.isEmpty()) {
            return new ReleasePublicationAuthorizationResult(false);
        }
        return new ReleasePublicationAuthorizationResult(ReleaseAuthorization.authorize(
                request.tenantId(),
                new EvaluationTarget(request.assistantVersionId(), request.assistantVersionRevision()),
                decision.get(),
                run.get()).allowed());
    }
}
