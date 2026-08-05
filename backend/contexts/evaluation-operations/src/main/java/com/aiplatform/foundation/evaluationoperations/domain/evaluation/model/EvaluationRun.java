package com.aiplatform.foundation.evaluationoperations.domain.evaluation.model;

import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.observation.EvaluationMetrics;
import java.time.Instant;
import java.util.Objects;

/**
 * 一次冻结目标、数据集、门禁和指标实现版本的评测运行根。
 */
public final class EvaluationRun implements AggregateRoot<EvaluationRunId> {

    private final TenantId tenantId;
    private final EvaluationRunId id;
    private final EvaluationTarget target;
    private final EvaluationDatasetSnapshot dataset;
    private final QualityGateSnapshot gate;
    private final String metricsImplementationVersion;
    private final EvaluationRunStatus status;
    private final EvaluationMetrics metrics;
    private final long version;
    private final Instant createdAt;
    private final Instant completedAt;

    private EvaluationRun(
            TenantId tenantId,
            EvaluationRunId id,
            EvaluationTarget target,
            EvaluationDatasetSnapshot dataset,
            QualityGateSnapshot gate,
            String metricsImplementationVersion,
            EvaluationRunStatus status,
            EvaluationMetrics metrics,
            long version,
            Instant createdAt,
            Instant completedAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "评测运行租户不能为空");
        this.id = Objects.requireNonNull(id, "评测运行标识不能为空");
        this.target = Objects.requireNonNull(target, "评测目标不能为空");
        this.dataset = Objects.requireNonNull(dataset, "评测数据集快照不能为空");
        this.gate = Objects.requireNonNull(gate, "质量门禁快照不能为空");
        if (metricsImplementationVersion == null || metricsImplementationVersion.isBlank()
                || !metricsImplementationVersion.equals(metricsImplementationVersion.trim())) {
            throw new IllegalArgumentException("评测指标实现版本不正确");
        }
        this.metricsImplementationVersion = metricsImplementationVersion;
        this.status = Objects.requireNonNull(status, "评测运行状态不能为空");
        this.metrics = metrics;
        if (version < 0L) {
            throw new IllegalArgumentException("评测运行修订不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "评测运行创建时间不能为空");
        this.completedAt = completedAt;
        if ((status == EvaluationRunStatus.RUNNING) != (metrics == null || completedAt == null)) {
            throw new IllegalArgumentException("评测运行终态与指标事实不一致");
        }
    }

    /**
     * 创建尚未产生终态结论的冻结评测运行。
     */
    public static EvaluationRun start(
            TenantId tenantId,
            EvaluationRunId id,
            EvaluationTarget target,
            EvaluationDatasetSnapshot dataset,
            QualityGateSnapshot gate,
            String metricsImplementationVersion,
            Instant createdAt) {
        return new EvaluationRun(
                tenantId, id, target, dataset, gate, metricsImplementationVersion,
                EvaluationRunStatus.RUNNING, null, 0L, createdAt, null);
    }

    /**
     * 冻结本次运行的通过或失败结论。
     *
     * @throws IllegalStateException 当运行已进入终态时拒绝覆盖历史结论。
     */
    public EvaluationRun complete(EvaluationMetrics finalMetrics, boolean passed, Instant completedAt) {
        if (status != EvaluationRunStatus.RUNNING) {
            throw new IllegalStateException("评测运行已经进入终态");
        }
        return new EvaluationRun(
                tenantId, id, target, dataset, gate, metricsImplementationVersion,
                passed ? EvaluationRunStatus.PASSED : EvaluationRunStatus.FAILED,
                Objects.requireNonNull(finalMetrics, "评测指标不能为空"), version + 1L,
                createdAt, Objects.requireNonNull(completedAt, "评测完成时间不能为空"));
    }

    public TenantId tenantId() {
        return tenantId;
    }

    @Override
    public EvaluationRunId id() {
        return id;
    }

    public EvaluationTarget target() {
        return target;
    }

    public EvaluationDatasetSnapshot dataset() {
        return dataset;
    }

    public QualityGateSnapshot gate() {
        return gate;
    }

    public EvaluationRunStatus status() {
        return status;
    }

    public EvaluationMetrics metrics() {
        return metrics;
    }

    /**
     * 投影供发布决定消费的最小运行事实。
     *
     * @throws IllegalStateException 当运行尚未完成时拒绝形成发布事实。
     */
    public EvaluationRunReleaseFact releaseFact() {
        if (status == EvaluationRunStatus.RUNNING) {
            throw new IllegalStateException("运行中的评测不能形成发布事实");
        }
        return new EvaluationRunReleaseFact(tenantId, id, target, status);
    }
}
