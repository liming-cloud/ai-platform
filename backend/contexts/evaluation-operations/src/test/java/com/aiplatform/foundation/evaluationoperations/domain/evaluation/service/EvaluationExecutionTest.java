package com.aiplatform.foundation.evaluationoperations.domain.evaluation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetContentClassification;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetSnapshot;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRun;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationTarget;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.policy.QualityGatePolicy;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.observation.EvaluationObservation;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EvaluationExecutionTest {

    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);
    private static final TenantId OTHER_TENANT_ID = new TenantId(100000000000000002L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 应仅在冻结数据集和三项指标均达标时通过() {
        var dataset = publishedDataset(TENANT_ID);

        var completed = EvaluationExecution.complete(
                running(dataset),
                dataset,
                observation(TENANT_ID, 20, 17, 20, 19, 100, 98, 10, 0),
                CREATED_AT.plusSeconds(1));

        assertEquals(EvaluationRunStatus.PASSED, completed.run().status());
        assertEquals(0.85D, completed.run().metrics().recallAt10());
        assertEquals(0.95D, completed.run().metrics().citationCorrectness());
        assertEquals(0.98D, completed.run().metrics().correctRefusalRate());
    }

    @Test
    void 未发布或跨租户数据集不得形成通过结论() {
        var draft = EvaluationDataset.create(
                TENANT_ID,
                new EvaluationDatasetId(100000000000000011L),
                "private://evaluation/dataset-11",
                digest(),
                130,
                EvaluationDatasetContentClassification.SYNTHETIC,
                "evaluation-retention-v1",
                CREATED_AT);
        var published = publishedDataset(TENANT_ID);

        var draftResult = EvaluationExecution.complete(
                running(EvaluationDatasetSnapshot.from(draft)),
                draft,
                observation(TENANT_ID, 20, 20, 20, 20, 100, 100, 10, 0),
                CREATED_AT.plusSeconds(1));
        var crossTenantResult = EvaluationExecution.complete(
                running(published),
                published,
                observation(OTHER_TENANT_ID, 20, 20, 20, 20, 100, 100, 10, 0),
                CREATED_AT.plusSeconds(1));

        assertEquals(EvaluationRunStatus.FAILED, draftResult.run().status());
        assertEquals(EvaluationRunStatus.FAILED, crossTenantResult.run().status());
    }

    @Test
    void 零分母错误引用错误拒答第十一候选和执行失败均失败关闭() {
        var dataset = publishedDataset(TENANT_ID);

        var zeroDenominator = EvaluationExecution.complete(
                running(dataset), dataset,
                observation(TENANT_ID, 0, 0, 0, 0, 100, 100, 10, 0), CREATED_AT.plusSeconds(1));
        var incorrectCitation = EvaluationExecution.complete(
                running(dataset), dataset,
                observation(TENANT_ID, 20, 20, 20, 18, 100, 100, 10, 0), CREATED_AT.plusSeconds(1));
        var incorrectRefusal = EvaluationExecution.complete(
                running(dataset), dataset,
                observation(TENANT_ID, 20, 20, 20, 20, 100, 97, 10, 0), CREATED_AT.plusSeconds(1));
        var eleventhCandidate = EvaluationExecution.complete(
                running(dataset), dataset,
                observation(TENANT_ID, 20, 20, 20, 20, 100, 100, 11, 0), CREATED_AT.plusSeconds(1));
        var executionFailure = EvaluationExecution.complete(
                running(dataset), dataset,
                observation(TENANT_ID, 20, 20, 20, 20, 100, 100, 10, 1), CREATED_AT.plusSeconds(1));
        var unauthorizedEvidence = EvaluationExecution.complete(
                running(dataset), dataset,
                new EvaluationObservation(TENANT_ID, 20, 20, 20, 20, 100, 100, 10, 0, true),
                CREATED_AT.plusSeconds(1));

        assertEquals(EvaluationRunStatus.FAILED, zeroDenominator.run().status());
        assertEquals(EvaluationRunStatus.FAILED, incorrectCitation.run().status());
        assertEquals(EvaluationRunStatus.FAILED, incorrectRefusal.run().status());
        assertEquals(EvaluationRunStatus.FAILED, eleventhCandidate.run().status());
        assertEquals(EvaluationRunStatus.FAILED, executionFailure.run().status());
        assertEquals(EvaluationRunStatus.FAILED, unauthorizedEvidence.run().status());
    }

    private static EvaluationDataset publishedDataset(TenantId tenantId) {
        return EvaluationDataset.create(
                        tenantId,
                        new EvaluationDatasetId(100000000000000010L),
                        "private://evaluation/dataset-10",
                        digest(),
                        130,
                        EvaluationDatasetContentClassification.SYNTHETIC,
                        "evaluation-retention-v1",
                        CREATED_AT)
                .publish();
    }

    private static EvaluationRun running(EvaluationDataset dataset) {
        return running(EvaluationDatasetSnapshot.from(dataset));
    }

    private static EvaluationRun running(EvaluationDatasetSnapshot dataset) {
        return EvaluationRun.start(
                TENANT_ID,
                new EvaluationRunId(100000000000000020L),
                new EvaluationTarget(100000000000000030L, 7L),
                dataset,
                QualityGatePolicy.qualityGateV1().snapshot(),
                "a".repeat(64),
                "evaluation-metrics-v1",
                CREATED_AT);
    }

    private static EvaluationObservation observation(
            TenantId tenantId,
            int expectedEvidenceCount,
            int retrievedExpectedEvidenceCount,
            int citationCount,
            int correctCitationCount,
            int expectedRefusalCount,
            int correctRefusalCount,
            int maximumRetrievedCandidateCount,
            int failedEntryCount) {
        return new EvaluationObservation(
                tenantId,
                expectedEvidenceCount,
                retrievedExpectedEvidenceCount,
                citationCount,
                correctCitationCount,
                expectedRefusalCount,
                correctRefusalCount,
                maximumRetrievedCandidateCount,
                failedEntryCount,
                false);
    }

    private static String digest() {
        return "a".repeat(64);
    }
}
