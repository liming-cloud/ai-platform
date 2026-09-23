package com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerCitation;
import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerExecutionObservation;
import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerResult;
import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerUsage;
import com.aiplatform.foundation.assistantdelivery.application.answer.facade.ModelInvocationTrace;
import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerExecutionException;
import com.aiplatform.foundation.assistantdelivery.application.answer.service.AssistantTrustedAnswerExecutor;
import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.DraftAssistantVersionSnapshot;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.DraftAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.command.RunAssistantDraftTestCommand;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.dto.AssistantTestRunTerminalCommit;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.port.out.AssistantTestRunTerminalCommitter;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestQuotaSnapshot;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRun;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRunStatus;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.policy.AssistantTestQuotaPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.repository.AssistantTestRunRepository;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantTestRunServiceTest {

    @Test
    void rejectsBeforeDraftLookupWhenKnowledgeReadIsAbsent() {
        var drafts = mock(DraftAssistantVersionLookup.class);
        var repository = mock(AssistantTestRunRepository.class);
        var terminalCommitter = mock(AssistantTestRunTerminalCommitter.class);
        var service = new AssistantTestRunService(
                drafts, repository, terminalCommitter, new AssistantTestQuotaPolicy(4, 2, Duration.ofMinutes(5)),
                mock(AssistantTrustedAnswerExecutor.class), Clock.systemUTC(), generator());

        assertThrows(RuntimeException.class, () -> service.run(new RunAssistantDraftTestCommand(
                context(), "11", "测试问题")));

        verifyNoInteractions(drafts, repository);
    }

    @Test
    void persistsOnlyMinimalAnsweredFactsAndReturnsCurrentAnswer() {
        var drafts = mock(DraftAssistantVersionLookup.class);
        var repository = mock(AssistantTestRunRepository.class);
        var terminalCommitter = mock(AssistantTestRunTerminalCommitter.class);
        var executor = mock(AssistantTrustedAnswerExecutor.class);
        var quota = new AssistantTestQuotaPolicy(4, 2, Duration.ofMinutes(5));
        var now = Instant.parse("2026-07-31T00:00:00Z");
        when(drafts.find(context("knowledge:read"), 11L)).thenReturn(Optional.of(draft()));
        when(repository.claim(org.mockito.ArgumentMatchers.any(AssistantTestRun.class),
                org.mockito.ArgumentMatchers.eq(quota))).thenAnswer(invocation -> {
                    var proposed = invocation.getArgument(0, AssistantTestRun.class);
                    return proposed.acceptQuota(AssistantTestQuotaSnapshot.forAcceptedTest(now, quota, 1));
                });
        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("测试问题"))).thenReturn(new AssistantTrustedAnswerResult(
                AssistantTrustedAnswerResult.Outcome.ANSWERED, "当前回答\n\n引用：[1]",
                List.of(new AssistantTrustedAnswerCitation(1, 21, 2, digest(), 0, "证据摘录", 0.9)),
                new AssistantTrustedAnswerUsage(3, 5, 8), 1, 5, 7));
        var service = new AssistantTestRunService(
                drafts, repository, terminalCommitter, quota, executor,
                Clock.fixed(now, java.time.ZoneOffset.UTC), generator());

        var result = service.run(new RunAssistantDraftTestCommand(context("knowledge:read"), "11", "测试问题"));

        assertEquals(AssistantTestRunStatus.ANSWERED, result.status());
        assertEquals("当前回答\n\n引用：[1]", result.answer());
        var completed = ArgumentCaptor.forClass(AssistantTestRunTerminalCommit.class);
        verify(terminalCommitter).commit(completed.capture());
        assertEquals(AssistantTestRunStatus.ANSWERED, completed.getValue().run().status());
        assertEquals(1, completed.getValue().run().citations().size());
        assertEquals(3, completed.getValue().run().usage().inputTokens());
        assertEquals(5, completed.getValue().run().usage().outputTokens());
    }

    @Test
    void persistsRefusedTerminalWithoutAnswerWhenEvidenceIsInsufficient() {
        var fixture = fixture();
        when(fixture.executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(AssistantTrustedAnswerResult.refused(0, 5, 7));

        var result = fixture.service.run(new RunAssistantDraftTestCommand(context("knowledge:read"), "11", "测试问题"));

        assertEquals(AssistantTestRunStatus.REFUSED, result.status());
        assertEquals(null, result.answer());
        var completed = ArgumentCaptor.forClass(AssistantTestRunTerminalCommit.class);
        verify(fixture.terminalCommitter).commit(completed.capture());
        assertEquals(AssistantTestRunStatus.REFUSED, completed.getValue().run().status());
        assertEquals(0, completed.getValue().run().citations().size());
    }

    @Test
    void closesClaimedRunAsFailedWhenTrustedExecutionThrows() {
        var fixture = fixture();
        var trace = new ModelInvocationTrace(List.of(
                new ModelInvocationTrace.Attempt(
                        1, 41, 5, 51, 6, ModelInvocationTrace.Outcome.TRANSIENT_FAILURE, 0, 0, 0, 1)));
        when(fixture.executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenThrow(new AssistantTrustedAnswerExecutionException(
                        new AssistantTrustedAnswerExecutionObservation(1, 5, 7, trace),
                        new IllegalStateException("模型失败")));

        assertThrows(AssistantTrustedAnswerExecutionException.class, () -> fixture.service.run(
                new RunAssistantDraftTestCommand(context("knowledge:read"), "11", "测试问题")));

        var completed = ArgumentCaptor.forClass(AssistantTestRunTerminalCommit.class);
        verify(fixture.terminalCommitter).commit(completed.capture());
        assertEquals(AssistantTestRunStatus.FAILED, completed.getValue().run().status());
        assertEquals(trace, completed.getValue().modelInvocationTrace());
    }

    private static EntityIdGenerator generator() {
        return () -> 900000000000000001L;
    }

    private static TrustedRequestContext context(String... permissions) {
        return new TrustedRequestContext(
                new TenantId(700000000000000001L), new SubjectId(720000000000000001L),
                new MembershipId(730000000000000001L), CredentialType.PERSON,
                AccessChannel.CONSOLE, 1, java.util.Arrays.stream(permissions)
                        .map(com.aiplatform.foundation.security.identity.PermissionCode::new)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()), "trace-assistant-test-001");
    }

    private static DraftAssistantVersionSnapshot draft() {
        return new DraftAssistantVersionSnapshot(
                11, 7, 2, 21, 31, 3,
                new GovernedChatModelBinding(41, 5, 51, 6, 256),
                new GovernedEmbeddingModelBinding(42, 7, 52, 8, 4),
                "仅根据证据回答", 256, 4, 0.2,
                CitationPolicy.REQUIRED, RefusalPolicy.INSUFFICIENT_EVIDENCE);
    }

    private static String digest() {
        return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    }

    private static Fixture fixture() {
        var drafts = mock(DraftAssistantVersionLookup.class);
        var repository = mock(AssistantTestRunRepository.class);
        var terminalCommitter = mock(AssistantTestRunTerminalCommitter.class);
        var executor = mock(AssistantTrustedAnswerExecutor.class);
        var quota = new AssistantTestQuotaPolicy(4, 2, Duration.ofMinutes(5));
        var now = Instant.parse("2026-07-31T00:00:00Z");
        when(drafts.find(context("knowledge:read"), 11L)).thenReturn(Optional.of(draft()));
        when(repository.claim(org.mockito.ArgumentMatchers.any(AssistantTestRun.class),
                org.mockito.ArgumentMatchers.eq(quota))).thenAnswer(invocation -> invocation
                .getArgument(0, AssistantTestRun.class)
                .acceptQuota(AssistantTestQuotaSnapshot.forAcceptedTest(now, quota, 1)));
        return new Fixture(repository, terminalCommitter, executor, new AssistantTestRunService(
                drafts, repository, terminalCommitter, quota, executor,
                Clock.fixed(now, java.time.ZoneOffset.UTC), generator()));
    }

    private record Fixture(
            AssistantTestRunRepository repository,
            AssistantTestRunTerminalCommitter terminalCommitter,
            AssistantTrustedAnswerExecutor executor,
            AssistantTestRunService service) {
    }
}
