package com.aiplatform.foundation.evaluationoperations.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.evaluationoperations.api.model.ListQueryAuditRecordsQuery;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryUsageQuery;
import com.aiplatform.foundation.evaluationoperations.domain.authorization.OperationsPermissionDeniedException;
import com.aiplatform.foundation.evaluationoperations.domain.usage.OperationsQuotaPolicy;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.api.operations.QueryAuditFact;
import com.aiplatform.foundation.trustedquery.api.operations.QueryAuditFactPage;
import com.aiplatform.foundation.trustedquery.api.operations.QueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.api.operations.QueryUsageFactQuery;
import com.aiplatform.foundation.trustedquery.api.operations.QueryUsageFacts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultOperationsQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T06:00:00Z");
    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final OperationsQuotaPolicy QUOTA =
            new OperationsQuotaPolicy(100, 4);

    @Test
    void 默认汇总当前自然日并组合真实用量与固定限额() {
        var facts = new RecordingFacts();
        var service = service(facts);

        var usage = service.usage(new QueryUsageQuery(
                context("usage:read"),
                null,
                null));

        assertEquals(Instant.parse("2026-07-27T00:00:00Z"), usage.from());
        assertEquals(Instant.parse("2026-07-28T00:00:00Z"), usage.to());
        assertEquals(7, usage.acceptedQueries());
        assertEquals(32, usage.totalTokens());
        assertEquals(8, usage.dailyQuota().used());
        assertEquals(100, usage.dailyQuota().limit());
        assertEquals(2, usage.concurrentQuota().used());
        assertEquals(4, usage.concurrentQuota().limit());
        assertEquals(TENANT, facts.usageQuery.tenantId());
    }

    @Test
    void 审计列表只映射脱敏查询事实并保留不透明游标() {
        var service = service(new RecordingFacts());

        var page = service.audit(new ListQueryAuditRecordsQuery(
                context("audit:read"),
                null,
                20));

        assertEquals(1, page.items().size());
        assertEquals("a".repeat(64), page.items().getFirst().questionDigest());
        assertEquals("next-cursor", page.nextCursor());
    }

    @Test
    void 用量和审计权限彼此独立且统计范围最多三十一天() {
        var service = service(new RecordingFacts());

        assertThrows(
                OperationsPermissionDeniedException.class,
                () -> service.usage(new QueryUsageQuery(
                        context("audit:read"),
                        null,
                        null)));
        assertThrows(
                OperationsPermissionDeniedException.class,
                () -> service.audit(new ListQueryAuditRecordsQuery(
                        context("usage:read"),
                        null,
                        20)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.usage(new QueryUsageQuery(
                        context("usage:read"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-02-02T00:00:00Z"))));
    }

    private static DefaultOperationsQueryService service(
            QueryOperationsFactSource facts) {
        return new DefaultOperationsQueryService(
                facts,
                QUOTA,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TrustedRequestContext context(String... permissions) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(7200000000000000002L),
                new MembershipId(7300000000000000002L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                java.util.Arrays.stream(permissions)
                        .map(PermissionCode::new)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                "trace-operations-001");
    }

    private static final class RecordingFacts
            implements QueryOperationsFactSource {

        private QueryUsageFactQuery usageQuery;

        @Override
        public QueryUsageFacts summarize(QueryUsageFactQuery query) {
            usageQuery = query;
            return new QueryUsageFacts(7, 4, 1, 1, 1, 20, 12, 32, 8, 2);
        }

        @Override
        public QueryAuditFactPage listAuditRecords(
                TenantId tenantId,
                String cursor,
                int limit) {
            return new QueryAuditFactPage(
                    List.of(new QueryAuditFact(
                            "430000000000000001",
                            new MembershipId(730000000000000003L),
                            "420000000000000001",
                            "a".repeat(64),
                            "ANSWERED",
                            null,
                            20,
                            12,
                            32,
                            NOW.minusSeconds(10),
                            NOW.minusSeconds(9))),
                    "next-cursor");
        }
    }
}
