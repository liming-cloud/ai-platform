package com.aiplatform.foundation.trustedquery.application.feedback.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.application.feedback.command.RecordQueryFeedbackCommand;
import com.aiplatform.foundation.trustedquery.application.feedback.exception.QueryFeedbackDuplicateException;
import com.aiplatform.foundation.trustedquery.application.feedback.exception.QueryFeedbackUnavailableException;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedbackOutcome;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedbackReason;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.policy.QueryFeedbackRecording;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.repository.QueryFeedbackRepository;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryQuotaSnapshot;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRefusalReason;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRequesterId;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryFeedbackServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final QueryRunId RUN = new QueryRunId(200000000000000001L);
    private static final Instant NOW = Instant.parse("2026-07-30T16:00:00Z");

    private final QueryRunRepository runs = mock(QueryRunRepository.class);
    private final QueryFeedbackRepository feedback = mock(QueryFeedbackRepository.class);
    private final EntityIdGenerator identifiers = () -> 300000000000000001L;
    private QueryFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new QueryFeedbackService(
                runs, feedback, Clock.fixed(NOW, ZoneOffset.UTC), identifiers, com.aiplatform.foundation.trustedquery.application.run.service.QueryAuthorizationFixture.allowed());
    }

    @Test
    void 只允许原请求成员为已完成查询写入一次反馈() {
        when(runs.find(TENANT, RUN)).thenReturn(Optional.of(completed(2L)));
        when(feedback.record(any())).thenReturn(QueryFeedbackRecording.recorded());

        assertDoesNotThrow(() -> service.record(command(context(2L), QueryFeedbackOutcome.POSITIVE,
                QueryFeedbackReason.HELPFUL)));
        verify(feedback).record(any());
    }

    @Test
    void 运行中或非请求成员必须按不可用失败关闭() {
        when(runs.find(TENANT, RUN)).thenReturn(Optional.of(running(2L)));
        assertThrows(QueryFeedbackUnavailableException.class,
                () -> service.record(command(context(2L), QueryFeedbackOutcome.POSITIVE,
                        QueryFeedbackReason.HELPFUL)));

        when(runs.find(TENANT, RUN)).thenReturn(Optional.of(completed(2L)));
        assertThrows(QueryFeedbackUnavailableException.class,
                () -> service.record(command(context(3L), QueryFeedbackOutcome.POSITIVE,
                        QueryFeedbackReason.HELPFUL)));
    }

    @Test
    void 原子重复结果必须映射为冲突() {
        when(runs.find(TENANT, RUN)).thenReturn(Optional.of(completed(2L)));
        when(feedback.record(any())).thenReturn(QueryFeedbackRecording.duplicate());

        assertThrows(QueryFeedbackDuplicateException.class,
                () -> service.record(command(context(2L), QueryFeedbackOutcome.NEGATIVE,
                        QueryFeedbackReason.INCORRECT)));
    }

    private static RecordQueryFeedbackCommand command(
            TrustedRequestContext context,
            QueryFeedbackOutcome outcome,
            QueryFeedbackReason reason) {
        return new RecordQueryFeedbackCommand(context, RUN.toString(), outcome, reason);
    }

    private static QueryRun completed(long memberId) {
        return running(memberId).refuse(QueryRefusalReason.INSUFFICIENT_EVIDENCE, NOW.plusSeconds(1));
    }

    private static QueryRun running(long memberId) {
        return QueryRun.start(
                        TENANT,
                        RUN,
                        400000000000000001L,
                        new QueryRequesterId(memberId),
                        "a".repeat(64),
                        NOW)
                .acceptQuota(QueryQuotaSnapshot.forAcceptedQuery(
                        NOW, 10, 1, 2, Duration.ofMinutes(1)));
    }

    private static TrustedRequestContext context(long membershipId) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(100000000000000002L),
                new MembershipId(membershipId),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode("assistant:query")),
                "trc_feedback_test");
    }
}
