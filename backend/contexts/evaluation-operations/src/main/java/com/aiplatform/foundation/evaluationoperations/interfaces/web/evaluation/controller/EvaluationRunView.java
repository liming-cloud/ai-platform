package com.aiplatform.foundation.evaluationoperations.interfaces.web.evaluation.controller;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRun;
import java.time.Instant;

/**
 * 已完成评测运行的无正文投影；不暴露私有内容位置、输入、回答、证据正文或模型路由摘要。
 */
public record EvaluationRunView(
        String id,
        String assistantVersionId,
        long assistantVersionRevision,
        String datasetId,
        long datasetVersion,
        String gatePolicyVersion,
        String metricsImplementationVersion,
        String status,
        int expectedEvidenceCount,
        int retrievedExpectedEvidenceCount,
        int citationCount,
        int correctCitationCount,
        int expectedRefusalCount,
        int correctRefusalCount,
        int maximumRetrievedCandidateCount,
        int failedEntryCount,
        boolean unauthorizedEvidence,
        boolean metricsComputable,
        double recallAt10,
        double citationCorrectness,
        double correctRefusalRate,
        long version,
        Instant createdAt,
        Instant completedAt) {

    static EvaluationRunView from(EvaluationRun run) {
        var observation = run.observation();
        var metrics = run.metrics();
        if (observation == null || metrics == null || run.completedAt() == null) {
            throw new IllegalArgumentException("运行尚未形成可公开的终态投影");
        }
        return new EvaluationRunView(
                Long.toString(run.id().value()),
                Long.toString(run.target().assistantVersionId()),
                run.target().assistantVersionRevision(),
                Long.toString(run.dataset().datasetId().value()),
                run.dataset().datasetVersion(),
                run.gate().policyVersion(),
                run.metricsImplementationVersion(),
                run.status().name(),
                observation.expectedEvidenceCount(),
                observation.retrievedExpectedEvidenceCount(),
                observation.citationCount(),
                observation.correctCitationCount(),
                observation.expectedRefusalCount(),
                observation.correctRefusalCount(),
                observation.maximumRetrievedCandidateCount(),
                observation.failedEntryCount(),
                observation.unauthorizedEvidence(),
                metrics.computable(),
                metrics.recallAt10(),
                metrics.citationCorrectness(),
                metrics.correctRefusalRate(),
                run.version(),
                run.createdAt(),
                run.completedAt());
    }
}
