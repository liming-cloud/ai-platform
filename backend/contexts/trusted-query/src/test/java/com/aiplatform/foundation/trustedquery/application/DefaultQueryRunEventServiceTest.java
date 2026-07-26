package com.aiplatform.foundation.trustedquery.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.api.model.ListQueryRunEventsQuery;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunClaim;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.QueryRefusalReason;
import com.aiplatform.foundation.trustedquery.domain.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.QueryRunEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultQueryRunEventServiceTest {

    private static final TenantId TENANT =
            TenantId.parse("10000000-0000-0000-0000-000000000001");
    private static final UUID RUN =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-27T06:00:00Z");

    @Test
    void 只从可信租户按游标返回终态事件() {
        var repository = new RecordingRepository();
        var service = new DefaultQueryRunEventService(repository);

        var page = service.list(new ListQueryRunEventsQuery(
                context(),
                RUN,
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
                new SubjectId(UUID.randomUUID()),
                new MembershipId(UUID.randomUUID()),
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
                UUID.randomUUID(),
                context().membershipId(),
                "a".repeat(64),
                NOW);
        private final QueryRun completed = running.refuse(
                QueryRefusalReason.INSUFFICIENT_EVIDENCE,
                NOW.plusSeconds(1));
        private TenantId tenantId;
        private long afterSequence;

        @Override
        public QueryRunClaim claim(
                QueryRun queryRun,
                String idempotencyKey,
                String requestFingerprint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void complete(QueryRun queryRun, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<QueryRun> find(TenantId tenantId, UUID queryRunId) {
            return TENANT.equals(tenantId) && RUN.equals(queryRunId)
                    ? Optional.of(completed)
                    : Optional.empty();
        }

        @Override
        public List<QueryRunEvent> findEvents(
                TenantId value,
                UUID queryRunId,
                long after,
                int limit) {
            tenantId = value;
            afterSequence = after;
            return List.of(QueryRunEvent.completed(completed, 2));
        }
    }
}
