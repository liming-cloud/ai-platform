package com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.policy.AssistantTestQuotaPolicy;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantTestRunTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant STARTED_AT = Instant.parse("2026-07-30T12:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-30T12:00:03Z");
    private static final String QUESTION_DIGEST = "a".repeat(64);

    @Test
    void shouldFreezeDraftDependenciesAndQuestionDigestWhenStarted() {
        var run = start();

        assertEquals(AssistantTestRunStatus.RUNNING, run.status());
        assertEquals(QUESTION_DIGEST, run.questionDigest());
        assertEquals(300000000000000001L, run.target().assistantVersionId());
        assertNull(run.quota());
    }

    @Test
    void shouldRejectTerminalStateBeforeIndependentTestQuotaIsAccepted() {
        var run = start();

        assertThrows(
                IllegalStateException.class,
                () -> run.answer(
                        new AssistantTestUsage(10, 20),
                        List.of(citation()),
                        COMPLETED_AT));
    }

    @Test
    void shouldPersistOnlyMinimalTerminalFactsAfterIndependentQuotaIsAccepted() {
        var accepted = start().acceptQuota(AssistantTestQuotaSnapshot.forAcceptedTest(
                STARTED_AT,
                new AssistantTestQuotaPolicy(4, 2, Duration.ofMinutes(5)),
                1));

        var answered = accepted.answer(
                new AssistantTestUsage(10, 20),
                List.of(citation()),
                COMPLETED_AT);

        assertEquals(AssistantTestRunStatus.ANSWERED, answered.status());
        assertEquals(30, answered.usage().totalTokens());
        assertEquals(List.of(citation()), answered.citations());
        assertEquals(COMPLETED_AT, answered.completedAt());
    }

    @Test
    void shouldRejectNonContinuousMinimalCitationIdentifiers() {
        var accepted = start().acceptQuota(AssistantTestQuotaSnapshot.forAcceptedTest(
                STARTED_AT,
                new AssistantTestQuotaPolicy(4, 2, Duration.ofMinutes(5)),
                1));

        assertThrows(
                IllegalArgumentException.class,
                () -> accepted.answer(
                        new AssistantTestUsage(10, 20),
                        List.of(new AssistantTestCitation(2, 200000000000000001L, 3, "chunk-001", 0)),
                        COMPLETED_AT));
    }

    @Test
    void shouldRejectQuestionContentOrInvalidDigestInsteadOfRetainingIt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AssistantTestRun.start(
                        TENANT,
                        new AssistantTestRunId(400000000000000001L),
                        new AssistantTestRequesterId(100000000000000002L),
                        target(),
                        "题目正文不得进入测试历史",
                        STARTED_AT));
    }

    private static AssistantTestRun start() {
        return AssistantTestRun.start(
                TENANT,
                new AssistantTestRunId(400000000000000001L),
                new AssistantTestRequesterId(100000000000000002L),
                target(),
                QUESTION_DIGEST,
                STARTED_AT);
    }

    private static AssistantTestTarget target() {
        return new AssistantTestTarget(
                300000000000000001L,
                200000000000000001L,
                230000000000000001L,
                5,
                new GovernedChatModelBinding(500000000000000001L, 7, 6, 2, 1024),
                new GovernedEmbeddingModelBinding(500000000000000002L, 3, 8, 4, 1536),
                "只依据编号证据回答，并在结论后标注引用。",
                512,
                5,
                0.2,
                CitationPolicy.REQUIRED,
                RefusalPolicy.INSUFFICIENT_EVIDENCE);
    }

    private static AssistantTestCitation citation() {
        return new AssistantTestCitation(1, 200000000000000001L, 3, "chunk-001", 0);
    }
}
