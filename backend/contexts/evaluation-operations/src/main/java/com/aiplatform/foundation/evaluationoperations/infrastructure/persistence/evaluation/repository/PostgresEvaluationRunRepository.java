package com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRun;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunRepository;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.entity.EvaluationRunEntity;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.mapper.EvaluationRunMapper;
import java.util.Objects;

/**
 * 将已经由领域服务完成的评测终态原样写入 PostgreSQL，不补写通过条件。
 */
public final class PostgresEvaluationRunRepository implements EvaluationRunRepository {

    private final EvaluationRunMapper mapper;

    public PostgresEvaluationRunRepository(EvaluationRunMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "评测运行映射器不能为空");
    }

    @Override
    public void save(EvaluationRun run) {
        run = Objects.requireNonNull(run, "评测运行不能为空");
        if (run.status() == EvaluationRunStatus.RUNNING || run.observation() == null || run.metrics() == null) {
            throw new IllegalArgumentException("只能提交已完成的评测运行");
        }
        if (mapper.insert(toEntity(run)) != 1) {
            throw new IllegalStateException("评测运行提交失败");
        }
    }

    private static EvaluationRunEntity toEntity(EvaluationRun run) {
        var observation = run.observation();
        var metrics = run.metrics();
        var entity = new EvaluationRunEntity();
        entity.setId(run.id().value());
        entity.setTenantId(run.tenantId().value());
        entity.setAssistantVersionId(run.target().assistantVersionId());
        entity.setAssistantVersionRevision(run.target().assistantVersionRevision());
        entity.setDatasetId(run.dataset().datasetId().value());
        entity.setDatasetVersion(run.dataset().datasetVersion());
        entity.setDatasetContentDigest(run.dataset().contentDigest());
        entity.setGatePolicyVersion(run.gate().policyVersion());
        entity.setMinimumRecallAt10(run.gate().minimumRecallAt10());
        entity.setMinimumCitationCorrectness(run.gate().minimumCitationCorrectness());
        entity.setMinimumCorrectRefusalRate(run.gate().minimumCorrectRefusalRate());
        entity.setModelRouteFingerprint(run.modelRouteFingerprint());
        entity.setMetricsImplementationVersion(run.metricsImplementationVersion());
        entity.setStatus(run.status().name());
        entity.setExpectedEvidenceCount(observation.expectedEvidenceCount());
        entity.setRetrievedExpectedEvidenceCount(observation.retrievedExpectedEvidenceCount());
        entity.setCitationCount(observation.citationCount());
        entity.setCorrectCitationCount(observation.correctCitationCount());
        entity.setExpectedRefusalCount(observation.expectedRefusalCount());
        entity.setCorrectRefusalCount(observation.correctRefusalCount());
        entity.setMaximumRetrievedCandidateCount(observation.maximumRetrievedCandidateCount());
        entity.setFailedEntryCount(observation.failedEntryCount());
        entity.setUnauthorizedEvidence(observation.unauthorizedEvidence());
        entity.setMetricsComputable(metrics.computable());
        entity.setRecallAt10(metrics.recallAt10());
        entity.setCitationCorrectness(metrics.citationCorrectness());
        entity.setCorrectRefusalRate(metrics.correctRefusalRate());
        entity.setVersion(run.version());
        entity.setCreatedAt(run.createdAt());
        entity.setCompletedAt(run.completedAt());
        return entity;
    }
}
