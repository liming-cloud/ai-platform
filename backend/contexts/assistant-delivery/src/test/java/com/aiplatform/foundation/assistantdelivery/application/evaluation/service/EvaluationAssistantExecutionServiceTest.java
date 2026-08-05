package com.aiplatform.foundation.assistantdelivery.application.evaluation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.assistantdelivery.application.answer.dto.AssistantTrustedEvaluationResult;
import com.aiplatform.foundation.assistantdelivery.application.answer.dto.AssistantTrustedAnswerExecutionObservation;
import com.aiplatform.foundation.assistantdelivery.application.answer.service.AssistantTrustedAnswerExecutionException;
import com.aiplatform.foundation.assistantdelivery.application.answer.service.AssistantTrustedAnswerExecutor;
import com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantExecutionResult;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CreatorId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvaluationAssistantExecutionServiceTest {

    private static final TenantId TENANT = new TenantId(410000000000000001L);
    private static final AssistantVersionId VERSION = new AssistantVersionId(420000000000000001L);

    @Test
    void 受限评测只返回无正文的候选引用与结果事实() {
        var repository = mock(AssistantRepository.class);
        var executor = mock(AssistantTrustedAnswerExecutor.class);
        when(repository.findVersion(TENANT, VERSION)).thenReturn(Optional.of(draft()));
        when(executor.executeForEvaluation(eq(writer()), any(), eq("私有评测题目")))
                .thenReturn(new AssistantTrustedEvaluationResult(
                        AssistantTrustedEvaluationResult.Outcome.ANSWERED,
                        List.of(digest()), List.of(digest()), 1));
        var service = new EvaluationAssistantExecutionService(repository, executor);

        var target = service.freeze(writer(), VERSION.value()).orElseThrow();
        EvaluationAssistantExecutionResult result = service.execute(writer(), target, "私有评测题目");

        assertEquals(EvaluationAssistantExecutionResult.Outcome.ANSWERED, result.outcome());
        assertEquals(List.of(digest()), result.retrievedEvidenceStableIds());
        assertEquals(List.of(digest()), result.citedEvidenceStableIds());
        assertTrue(java.util.Arrays.stream(EvaluationAssistantExecutionResult.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .noneMatch(name -> name.contains("answer") || name.contains("question") || name.contains("input")));
    }

    @Test
    void 执行失败只能返回无正文失败事实() {
        var repository = mock(AssistantRepository.class);
        var executor = mock(AssistantTrustedAnswerExecutor.class);
        when(repository.findVersion(TENANT, VERSION)).thenReturn(Optional.of(draft()));
        when(executor.executeForEvaluation(eq(writer()), any(), eq("私有评测题目")))
                .thenThrow(new AssistantTrustedAnswerExecutionException(
                        new AssistantTrustedAnswerExecutionObservation(1, 1, 1),
                        new IllegalStateException("供应商原始失败")));
        var service = new EvaluationAssistantExecutionService(repository, executor);

        var result = service.execute(writer(), service.freeze(writer(), VERSION.value()).orElseThrow(), "私有评测题目");

        assertEquals(EvaluationAssistantExecutionResult.Outcome.FAILED, result.outcome());
        assertEquals(1, result.candidateCount());
        assertTrue(result.retrievedEvidenceStableIds().isEmpty());
        assertTrue(result.citedEvidenceStableIds().isEmpty());
    }

    private static AssistantVersion draft() {
        return AssistantVersion.governedDraft(
                TENANT, VERSION, new AssistantId(430000000000000001L), 1,
                new GovernedChatModelBinding(440000000000000001L, 1, 450000000000000001L, 1, 512),
                460000000000000001L, 470000000000000001L, 1,
                new GovernedEmbeddingModelBinding(480000000000000001L, 1, 490000000000000001L, 1, 1024),
                "只依据授权证据回答。", 256, 5, 0.5,
                CitationPolicy.REQUIRED, RefusalPolicy.INSUFFICIENT_EVIDENCE,
                new CreatorId(500000000000000001L), Instant.parse("2026-07-30T00:00:00Z"));
    }

    private static TrustedRequestContext writer() {
        return new TrustedRequestContext(
                TENANT, new SubjectId(510000000000000001L), new MembershipId(520000000000000001L),
                CredentialType.PERSON, AccessChannel.CONSOLE, 1,
                Set.of(new PermissionCode("assistant:write")), "trace-evaluation-assistant");
    }

    private static String digest() {
        return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }
}
