package com.aiplatform.foundation.trustedquery.application.run.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.application.run.query.ListQueryRunEventsQuery;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryRunClaim;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryQuotaPolicy;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryQuotaSnapshot;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRefusalReason;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunEvent;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRequesterId;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryRunEventServiceTest {

    private static final TenantId TENANT =
            new TenantId(100000000000000001L);
    private static final QueryRunId RUN =
            new QueryRunId(430000000000000001L);
    private static final Instant NOW = Instant.parse("2026-07-27T06:00:00Z");

    @Test
    void 只从可信租户按游标返回终态事件() {
        var repository = new RecordingRepository();
        var service = new QueryRunEventService(repository);

        var page = service.list(new ListQueryRunEventsQuery(
                context(),
                RUN.toString(),
                1,
                10));

        assertEquals(TENANT, repository.tenantId);
        assertEquals(1, repository.afterSequence);
        assertEquals(List.of(2L), page.items().stream()
                .map(event -> event.sequence())
                .toList());
        assertEquals("QUERY_REFUSED", page.items().getFirst().type());
        assertTrue(page.terminal());
        assertEquals(2, page.resumeAfterSequence());
    }

    private static TrustedRequestContext context() {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(7200000000000000003L),
                new MembershipId(7300000000000000003L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(
                        new PermissionCode("assistant:query"),
                        new PermissionCode("knowledge:read")),
                "trace-query-events");
    }

    private static final class RecordingRepository implements QueryRunRepository {

        private final QueryRun running = QueryRun.start(
                TENANT,
                RUN,
                420000000000000001L,
                new QueryRequesterId(context().membershipId().value()),
                "a".repeat(64),
                NOW)
                .acceptQuota(QueryQuotaSnapshot.forAcceptedQuery(
                        NOW,
                        10,
                        1,
                        2,
                        Duration.ofMinutes(2)));
        private final QueryRun completed = running.refuse(
                QueryRefusalReason.INSUFFICIENT_EVIDENCE,
                NOW.plusSeconds(1));
        private TenantId tenantId;
        private long afterSequence;

        @Override
        public QueryRunClaim claim(
                QueryRun queryRun,
                String idempotencyKey,
                String requestFingerprint,
                QueryQuotaPolicy quota) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void complete(QueryRun queryRun, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<QueryRun> find(TenantId tenantId, QueryRunId queryRunId) {
            return TENANT.equals(tenantId) && RUN.equals(queryRunId)
                    ? Optional.of(completed)
                    : Optional.empty();
        }

        @Override
        public List<QueryRunEvent> findEvents(
                TenantId value,
                QueryRunId queryRunId,
                long after,
                int limit) {
            tenantId = value;
            afterSequence = after;
            return List.of(QueryRunEvent.completed(completed, 2));
        }
    }
}
