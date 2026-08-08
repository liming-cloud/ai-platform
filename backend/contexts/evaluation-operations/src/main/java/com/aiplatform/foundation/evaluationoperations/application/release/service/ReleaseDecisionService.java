package com.aiplatform.foundation.evaluationoperations.application.release.service;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunReleaseFactLookup;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecision;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionId;
import com.aiplatform.foundation.evaluationoperations.domain.release.repository.ReleaseDecisionRepository;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.util.Objects;

/** 从同租户冻结评测运行形成不可变发布决定的应用编排。 */
public final class ReleaseDecisionService {

    private static final PermissionCode ASSISTANT_WRITE = new PermissionCode("assistant:write");
    private final EvaluationRunReleaseFactLookup runs;
    private final ReleaseDecisionRepository decisions;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    public ReleaseDecisionService(EvaluationRunReleaseFactLookup runs, ReleaseDecisionRepository decisions,
            EntityIdGenerator identifiers, Clock clock) {
        this.runs = Objects.requireNonNull(runs, "评测运行仓储不能为空");
        this.decisions = Objects.requireNonNull(decisions, "发布决定仓储不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public ReleaseDecision decide(TrustedRequestContext context, long evaluationRunId) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (!context.hasPermission(ASSISTANT_WRITE)) {
            throw new IllegalArgumentException("当前主体缺少助手写权限");
        }
        var run = runs.findById(context.tenantId(), new EvaluationRunId(evaluationRunId))
                .orElseThrow(() -> new IllegalStateException("可发布评测运行不存在"));
        var decision = ReleaseDecision.decide(
                new ReleaseDecisionId(identifiers.nextId()), run, clock.instant());
        decisions.save(decision);
        return decision;
    }
}
