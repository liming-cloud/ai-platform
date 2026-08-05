package com.aiplatform.foundation.evaluationoperations.domain.release.model;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunReleaseFact;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationTarget;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 由冻结评测运行唯一导出的无例外发布决定根。
 */
public final class ReleaseDecision implements AggregateRoot<ReleaseDecisionId> {

    private final ReleaseDecisionId id;
    private final TenantId tenantId;
    private final EvaluationRunId evaluationRunId;
    private final EvaluationTarget target;
    private final ReleaseDecisionStatus status;
    private final long version;
    private final Instant decidedAt;

    private ReleaseDecision(
            ReleaseDecisionId id,
            TenantId tenantId,
            EvaluationRunId evaluationRunId,
            EvaluationTarget target,
            ReleaseDecisionStatus status,
            long version,
            Instant decidedAt) {
        this.id = Objects.requireNonNull(id, "发布决定标识不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "发布决定租户不能为空");
        this.evaluationRunId = Objects.requireNonNull(evaluationRunId, "发布决定评测运行不能为空");
        this.target = Objects.requireNonNull(target, "发布决定目标不能为空");
        this.status = Objects.requireNonNull(status, "发布决定状态不能为空");
        if (version < 0L) {
            throw new IllegalArgumentException("发布决定修订不能为负数");
        }
        this.version = version;
        this.decidedAt = Objects.requireNonNull(decidedAt, "发布决定时间不能为空");
    }

    /**
     * 从一条冻结评测运行事实作出不可变决定；非通过运行只能被拒绝。
     */
    public static ReleaseDecision decide(
            ReleaseDecisionId id,
            EvaluationRunReleaseFact run,
            Instant decidedAt) {
        run = Objects.requireNonNull(run, "评测运行发布事实不能为空");
        return new ReleaseDecision(
                id,
                run.tenantId(),
                run.evaluationRunId(),
                run.target(),
                run.status() == EvaluationRunStatus.PASSED
                        ? ReleaseDecisionStatus.AUTHORIZED
                        : ReleaseDecisionStatus.REJECTED,
                0L,
                decidedAt);
    }

    @Override
    public ReleaseDecisionId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public EvaluationRunId evaluationRunId() {
        return evaluationRunId;
    }

    public EvaluationTarget target() {
        return target;
    }

    public ReleaseDecisionStatus status() {
        return status;
    }
}
