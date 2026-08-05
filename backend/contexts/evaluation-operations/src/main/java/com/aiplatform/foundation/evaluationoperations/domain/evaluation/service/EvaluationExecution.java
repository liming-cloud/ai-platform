package com.aiplatform.foundation.evaluationoperations.domain.evaluation.service;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRun;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.operation.EvaluationCompletion;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.observation.EvaluationMetrics;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.observation.EvaluationObservation;
import java.time.Instant;
import java.util.Objects;

/**
 * 收回评测完成不变量的无状态领域服务；输入和结果均不含私有正文。
 */
public final class EvaluationExecution {

    private EvaluationExecution() {
    }

    /**
     * 依据冻结快照完成一次评测，并将任何无法证明安全通过的情形关闭为失败。
     *
     * @throws IllegalStateException 当运行已进入终态时拒绝重复完成。
     */
    public static EvaluationCompletion complete(
            EvaluationRun run,
            EvaluationDataset dataset,
            EvaluationObservation observation,
            Instant completedAt) {
        run = Objects.requireNonNull(run, "评测运行不能为空");
        dataset = Objects.requireNonNull(dataset, "评测数据集不能为空");
        observation = Objects.requireNonNull(observation, "评测观察不能为空");
        completedAt = Objects.requireNonNull(completedAt, "评测完成时间不能为空");
        var metrics = EvaluationMetrics.from(observation);
        var passed = matchesFrozenDataset(run, dataset)
                && run.tenantId().equals(observation.tenantId())
                && metrics.computable()
                && observation.maximumRetrievedCandidateCount() <= 10
                && observation.failedEntryCount() == 0
                && !observation.unauthorizedEvidence()
                && metrics.recallAt10() >= run.gate().minimumRecallAt10()
                && metrics.citationCorrectness() >= run.gate().minimumCitationCorrectness()
                && metrics.correctRefusalRate() >= run.gate().minimumCorrectRefusalRate();
        return new EvaluationCompletion(run.complete(metrics, passed, completedAt));
    }

    private static boolean matchesFrozenDataset(EvaluationRun run, EvaluationDataset dataset) {
        return dataset.status() == EvaluationDatasetStatus.PUBLISHED
                && run.tenantId().equals(dataset.tenantId())
                && run.dataset().datasetId().equals(dataset.id())
                && run.dataset().datasetVersion() == dataset.version()
                && run.dataset().contentDigest().equals(dataset.contentDigest());
    }
}
