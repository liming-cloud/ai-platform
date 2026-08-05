package com.aiplatform.foundation.assistantdelivery.application.evaluation.service;

import com.aiplatform.foundation.assistantdelivery.application.answer.dto.AssistantTrustedAnswerTarget;
import com.aiplatform.foundation.assistantdelivery.application.answer.dto.AssistantTrustedEvaluationResult;
import com.aiplatform.foundation.assistantdelivery.application.answer.service.AssistantTrustedAnswerExecutionException;
import com.aiplatform.foundation.assistantdelivery.application.answer.service.AssistantTrustedAnswerExecutor;
import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.DraftAssistantVersionSnapshot;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantPermissionDeniedException;
import com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantExecution;
import com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantExecutionResult;
import com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantTarget;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionStatus;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 只在调用期内交接一条私有评测输入，并在 assistant-delivery 内复核冻结草稿目标。
 */
public final class EvaluationAssistantExecutionService implements EvaluationAssistantExecution {

    private static final PermissionCode WRITE = new PermissionCode("assistant:write");

    private final AssistantRepository assistants;
    private final AssistantTrustedAnswerExecutor executor;

    public EvaluationAssistantExecutionService(
            AssistantRepository assistants,
            AssistantTrustedAnswerExecutor executor) {
        this.assistants = Objects.requireNonNull(assistants, "助手仓储不能为空");
        this.executor = Objects.requireNonNull(executor, "可信回答执行门面不能为空");
    }

    @Override
    public Optional<EvaluationAssistantTarget> freeze(TrustedRequestContext context, long assistantVersionId) {
        requireWrite(context);
        return snapshot(context, assistantVersionId).map(EvaluationAssistantExecutionService::target);
    }

    @Override
    public EvaluationAssistantExecutionResult execute(
            TrustedRequestContext context,
            EvaluationAssistantTarget target,
            String input) {
        requireWrite(context);
        var frozenTarget = Objects.requireNonNull(target, "评测助手目标不能为空");
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("评测输入不能为空白");
        }
        var snapshot = snapshot(context, frozenTarget.assistantVersionId())
                .filter(value -> target(value).equals(frozenTarget))
                .orElseThrow(() -> new IllegalArgumentException("评测助手目标已变化或不可用"));
        try {
            return from(executor.executeForEvaluation(
                    context, AssistantTrustedAnswerTarget.from(snapshot.snapshot()), input));
        } catch (AssistantTrustedAnswerExecutionException exception) {
            return failed(exception.observation().candidateCount());
        } catch (RuntimeException exception) {
            return failed(null);
        }
    }

    private Optional<ResolvedSnapshot> snapshot(
            TrustedRequestContext context,
            long assistantVersionId) {
        if (assistantVersionId <= 0) {
            throw new IllegalArgumentException("助手版本标识必须是正整数");
        }
        return assistants.findVersion(context.tenantId(), new AssistantVersionId(assistantVersionId))
                .filter(version -> version.status() == AssistantVersionStatus.DRAFT)
                .filter(version -> version.usesGovernedModelConfigurations())
                .map(version -> new ResolvedSnapshot(
                        new DraftAssistantVersionSnapshot(
                                version.id().value(), version.assistantId().value(), version.number(),
                                version.knowledgeSpaceId(), version.indexVersionId(), version.indexVersionRevision(),
                                version.governedChat(), version.governedChatRoute(), version.governedEmbedding(),
                                version.systemInstruction(), version.maxOutputTokens(), version.retrievalLimit(),
                                version.minimumScore(), version.citationPolicy(), version.refusalPolicy()),
                        version.version()));
    }

    private static EvaluationAssistantTarget target(ResolvedSnapshot snapshot) {
        return new EvaluationAssistantTarget(
                snapshot.snapshot().assistantVersionId(), snapshot.revision(),
                snapshot.snapshot().modelRouteFingerprint());
    }

    private static EvaluationAssistantExecutionResult from(AssistantTrustedEvaluationResult result) {
        return new EvaluationAssistantExecutionResult(
                result.outcome() == AssistantTrustedEvaluationResult.Outcome.ANSWERED
                        ? EvaluationAssistantExecutionResult.Outcome.ANSWERED
                        : EvaluationAssistantExecutionResult.Outcome.REFUSED_INSUFFICIENT_EVIDENCE,
                result.candidateCount(), result.retrievedEvidenceStableIds(), result.citedEvidenceStableIds());
    }

    private static EvaluationAssistantExecutionResult failed(Integer candidateCount) {
        return new EvaluationAssistantExecutionResult(
                EvaluationAssistantExecutionResult.Outcome.FAILED, candidateCount, List.of(), List.of());
    }

    private static void requireWrite(TrustedRequestContext context) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (!context.hasPermission(WRITE)) {
            throw new AssistantPermissionDeniedException();
        }
    }

    private record ResolvedSnapshot(DraftAssistantVersionSnapshot snapshot, long revision) {
    }
}
