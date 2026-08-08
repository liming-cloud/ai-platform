package com.aiplatform.foundation.evaluationoperations.domain.evaluation.model;

import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.observation.EvaluationMetrics;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.observation.EvaluationObservation;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 一次冻结目标、数据集、门禁和指标实现版本的评测运行根。
 */
public final class EvaluationRun implements AggregateRoot<EvaluationRunId> {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final TenantId tenantId;
    private final EvaluationRunId id;
    private final EvaluationTarget target;
    private final EvaluationDatasetSnapshot dataset;
    private final QualityGateSnapshot gate;
    private final String modelRouteFingerprint;
    private final String metricsImplementationVersion;
    private final EvaluationRunStatus status;
    private final EvaluationObservation observation;
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
            String modelRouteFingerprint,
            String metricsImplementationVersion,
            EvaluationRunStatus status,
            EvaluationObservation observation,
            EvaluationMetrics metrics,
            long version,
            Instant createdAt,
            Instant completedAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "评测运行租户不能为空");
        this.id = Objects.requireNonNull(id, "评测运行标识不能为空");
        this.target = Objects.requireNonNull(target, "评测目标不能为空");
        this.dataset = Objects.requireNonNull(dataset, "评测数据集快照不能为空");
        this.gate = Objects.requireNonNull(gate, "质量门禁快照不能为空");
        if (modelRouteFingerprint == null || !SHA256.matcher(modelRouteFingerprint).matches()) {
            throw new IllegalArgumentException("评测模型路由摘要格式不正确");
        }
        this.modelRouteFingerprint = modelRouteFingerprint;
        if (metricsImplementationVersion == null || metricsImplementationVersion.isBlank()
                || !metricsImplementationVersion.equals(metricsImplementationVersion.trim())) {
            throw new IllegalArgumentException("评测指标实现版本不正确");
        }
        this.metricsImplementationVersion = metricsImplementationVersion;
        this.status = Objects.requireNonNull(status, "评测运行状态不能为空");
        this.observation = observation;
        this.metrics = metrics;
        if (version < 0L) {
            throw new IllegalArgumentException("评测运行修订不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "评测运行创建时间不能为空");
        this.completedAt = completedAt;
        if ((status == EvaluationRunStatus.RUNNING)
                != (observation == null || metrics == null || completedAt == null)) {
            throw new IllegalArgumentException("评测运行终态与观察、指标事实不一致");
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
            String modelRouteFingerprint,
            String metricsImplementationVersion,
            Instant createdAt) {
        return new EvaluationRun(
                tenantId, id, target, dataset, gate, modelRouteFingerprint, metricsImplementationVersion,
                EvaluationRunStatus.RUNNING, null, null, 0L, createdAt, null);
    }

    /**
     * 从已验证的持久化运行事实恢复聚合根；该工厂不接受任何私有评测正文。
     */
    public static EvaluationRun restore(
            TenantId tenantId,
            EvaluationRunId id,
            EvaluationTarget target,
            EvaluationDatasetSnapshot dataset,
            QualityGateSnapshot gate,
            String modelRouteFingerprint,
            String metricsImplementationVersion,
            EvaluationRunStatus status,
            EvaluationObservation observation,
            EvaluationMetrics metrics,
            long version,
            Instant createdAt,
            Instant completedAt) {
        return new EvaluationRun(
                tenantId, id, target, dataset, gate, modelRouteFingerprint, metricsImplementationVersion,
                status, observation, metrics, version, createdAt, completedAt);
    }

    /**
     * 冻结本次运行的通过或失败结论。
     *
     * @throws IllegalStateException 当运行已进入终态时拒绝覆盖历史结论。
     */
    public EvaluationRun complete(
            EvaluationObservation finalObservation,
            EvaluationMetrics finalMetrics,
            boolean passed,
            Instant completedAt) {
        if (status != EvaluationRunStatus.RUNNING) {
            throw new IllegalStateException("评测运行已经进入终态");
        }
        return new EvaluationRun(
                tenantId, id, target, dataset, gate, modelRouteFingerprint, metricsImplementationVersion,
                passed ? EvaluationRunStatus.PASSED : EvaluationRunStatus.FAILED,
                Objects.requireNonNull(finalObservation, "评测观察不能为空"),
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

    /** 返回由 assistant-delivery 提供且随运行冻结的受控模型路由摘要。 */
    public String modelRouteFingerprint() {
        return modelRouteFingerprint;
    }

    public String metricsImplementationVersion() {
        return metricsImplementationVersion;
    }

    public EvaluationRunStatus status() {
        return status;
    }

    public EvaluationMetrics metrics() {
        return metrics;
    }

    /**
     * 返回形成冻结指标的最小计数观察；运行中尚无该事实。
     */
    public EvaluationObservation observation() {
        return observation;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
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
        return new EvaluationRunReleaseFact(tenantId, id, target, gate.policyVersion(), status);
    }
}
