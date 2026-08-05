package com.aiplatform.foundation.evaluationoperations.application.evaluation.service;

import com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantExecution;
import com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantExecutionResult;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetEntryReader;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetPrivateContentStore;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetEntry;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationRunExecutionRequest;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetSnapshot;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRun;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationTarget;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.observation.EvaluationObservation;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.policy.QualityGatePolicy;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationDatasetRepository;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunRepository;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.service.EvaluationExecution;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.io.IOException;
import java.time.Clock;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 编排私有数据集的逐条评测；仅在调用期内传递输入明文，持久化结果不含正文。
 */
public final class EvaluationRunExecutionService {

    private static final PermissionCode ASSISTANT_WRITE = new PermissionCode("assistant:write");
    private static final String METRICS_IMPLEMENTATION_VERSION = "evaluation-metrics-v1";

    private final EvaluationDatasetRepository datasets;
    private final EvaluationRunRepository runs;
    private final EvaluationDatasetPrivateContentStore contents;
    private final EvaluationAssistantExecution assistant;
    private final EvaluationDatasetEntryReader entries;
    private final QualityGatePolicy gate;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    public EvaluationRunExecutionService(
            EvaluationDatasetRepository datasets,
            EvaluationRunRepository runs,
            EvaluationDatasetPrivateContentStore contents,
            EvaluationAssistantExecution assistant,
            EvaluationDatasetEntryReader entries,
            QualityGatePolicy gate,
            EntityIdGenerator identifiers,
            Clock clock) {
        this.datasets = Objects.requireNonNull(datasets, "评测数据集仓储不能为空");
        this.runs = Objects.requireNonNull(runs, "评测运行仓储不能为空");
        this.contents = Objects.requireNonNull(contents, "评测私有内容存储不能为空");
        this.assistant = Objects.requireNonNull(assistant, "评测助手执行门面不能为空");
        this.entries = Objects.requireNonNull(entries, "评测条目读取器不能为空");
        this.gate = Objects.requireNonNull(gate, "质量门禁策略不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 以当前令牌租户冻结草稿助手和已发布数据集，并提交无正文的终态运行。
     *
     * @throws IllegalArgumentException 当令牌不含 `assistant:write` 时拒绝。
     * @throws IllegalStateException 当数据集或可评测草稿助手不存在时拒绝。
     */
    public EvaluationRun execute(
            TrustedRequestContext context,
            EvaluationRunExecutionRequest request) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        request = Objects.requireNonNull(request, "评测运行请求不能为空");
        requireWrite(context);
        var dataset = datasets.findById(context.tenantId(), new EvaluationDatasetId(request.datasetId()))
                .filter(value -> value.status() == EvaluationDatasetStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalStateException("可评测数据集不存在"));
        var assistantTarget = assistant.freeze(context, request.assistantVersionId())
                .orElseThrow(() -> new IllegalStateException("可评测草稿助手不存在"));
        var run = EvaluationRun.start(
                context.tenantId(),
                new EvaluationRunId(identifiers.nextId()),
                new EvaluationTarget(
                        assistantTarget.assistantVersionId(),
                        assistantTarget.assistantVersionRevision()),
                EvaluationDatasetSnapshot.from(dataset),
                gate.snapshot(),
                assistantTarget.modelRouteFingerprint(),
                METRICS_IMPLEMENTATION_VERSION,
                clock.instant());
        var observation = observe(context, dataset, assistantTarget);
        var completed = EvaluationExecution.complete(run, dataset, observation, clock.instant()).run();
        runs.save(completed);
        return completed;
    }

    private EvaluationObservation observe(
            TrustedRequestContext context,
            EvaluationDataset dataset,
            com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantTarget target) {
        var accumulator = new ObservationAccumulator(context);
        try (var content = contents.open(dataset.privateContentReference())) {
            var count = entries.read(content, entry -> accumulator.accept(entry, assistant.execute(context, target, entry.input())));
            if (count != dataset.entryCount()) {
                accumulator.recordFailure();
            }
        } catch (IOException | RuntimeException exception) {
            accumulator.recordFailure();
        }
        return accumulator.toObservation();
    }

    private static void requireWrite(TrustedRequestContext context) {
        if (!context.hasPermission(ASSISTANT_WRITE)) {
            throw new IllegalArgumentException("当前主体缺少助手写权限");
        }
    }

    private static final class ObservationAccumulator {

        private final TrustedRequestContext context;
        private int expectedEvidenceCount;
        private int retrievedExpectedEvidenceCount;
        private int citationCount;
        private int correctCitationCount;
        private int expectedRefusalCount;
        private int correctRefusalCount;
        private int maximumRetrievedCandidateCount;
        private int failedEntryCount;
        private boolean unauthorizedEvidence;

        private ObservationAccumulator(TrustedRequestContext context) {
            this.context = context;
        }

        private void accept(EvaluationDatasetEntry entry, EvaluationAssistantExecutionResult result) {
            var expectedEvidence = entry.expectedEvidenceStableIds();
            var retrieved = distinct(result.retrievedEvidenceStableIds());
            var citations = distinct(result.citedEvidenceStableIds());
            maximumRetrievedCandidateCount = Math.max(maximumRetrievedCandidateCount, result.candidateCount());
            if (entry.expectedOutcome() == EvaluationDatasetEntry.ExpectedOutcome.ANSWER) {
                expectedEvidenceCount += expectedEvidence.size();
                retrievedExpectedEvidenceCount += intersectionSize(expectedEvidence, retrieved);
            } else {
                expectedRefusalCount++;
                if (result.outcome() == EvaluationAssistantExecutionResult.Outcome.REFUSED_INSUFFICIENT_EVIDENCE
                        && citations.isEmpty()) {
                    correctRefusalCount++;
                }
            }
            citationCount += citations.size();
            correctCitationCount += intersectionSize(expectedEvidence, citations);
            unauthorizedEvidence |= !expectedEvidence.containsAll(citations);
            if (result.outcome() == EvaluationAssistantExecutionResult.Outcome.FAILED) {
                recordFailure();
            }
        }

        private void recordFailure() {
            failedEntryCount++;
        }

        private EvaluationObservation toObservation() {
            return new EvaluationObservation(
                    context.tenantId(),
                    expectedEvidenceCount,
                    retrievedExpectedEvidenceCount,
                    citationCount,
                    correctCitationCount,
                    expectedRefusalCount,
                    correctRefusalCount,
                    maximumRetrievedCandidateCount,
                    failedEntryCount,
                    unauthorizedEvidence);
        }

        private static Set<String> distinct(java.util.List<String> values) {
            return new HashSet<>(values);
        }

        private static int intersectionSize(Set<String> left, Set<String> right) {
            return (int) left.stream().filter(right::contains).count();
        }
    }
}
