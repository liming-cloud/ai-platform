package com.aiplatform.foundation.trustedquery.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ModelUsage;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueryRunTest {

    private static final Instant NOW = Instant.parse("2026-07-27T05:00:00Z");

    @Test
    void 可信回答必须携带连续引用并且只能完成一次() {
        var running = running();
        var citation = new QueryCitation(
                1,
                220000000000000001L,
                1,
                "a".repeat(64),
                0,
                "牛奶位于一层冷藏区。",
                0.9);

        var answered = running.answer(
                "牛奶位于一层。",
                List.of(citation),
                new ModelUsage(10, 5, 15),
                NOW.plusSeconds(1));

        assertEquals(QueryRunStatus.ANSWERED, answered.status());
        assertEquals(1, answered.version());
        assertEquals(15, answered.usage().totalTokens());
        assertThrows(
                IllegalStateException.class,
                () -> answered.refuse(
                        QueryRefusalReason.INSUFFICIENT_EVIDENCE,
                        NOW.plusSeconds(2)));
    }

    @Test
    void 没有引用不能形成可信回答() {
        assertThrows(
                IllegalArgumentException.class,
                () -> running().answer(
                        "没有来源的回答",
                        List.of(),
                        ModelUsage.UNKNOWN,
                        NOW.plusSeconds(1)));
    }

    @Test
    void 未占用配额的查询提案不能执行且占用事实会进入终态() {
        var proposed = proposed();
        var quota = new QueryQuotaSnapshot(
                Instant.parse("2026-07-27T00:00:00Z"),
                Instant.parse("2026-07-28T00:00:00Z"),
                10,
                3,
                2,
                NOW.plusSeconds(30));

        assertThrows(
                IllegalStateException.class,
                () -> proposed.refuse(
                        QueryRefusalReason.INSUFFICIENT_EVIDENCE,
                        NOW.plusSeconds(1)));

        var accepted = proposed.acceptQuota(quota);
        var refused = accepted.refuse(
                QueryRefusalReason.INSUFFICIENT_EVIDENCE,
                NOW.plusSeconds(1));

        assertEquals(quota, refused.quota());
        assertThrows(
                IllegalStateException.class,
                () -> accepted.acceptQuota(quota));
    }

    private static QueryRun running() {
        return proposed().acceptQuota(new QueryQuotaSnapshot(
                Instant.parse("2026-07-27T00:00:00Z"),
                Instant.parse("2026-07-28T00:00:00Z"),
                10,
                1,
                2,
                NOW.plusSeconds(30)));
    }

    private static QueryRun proposed() {
        return QueryRun.start(
                new TenantId(7100000000000000004L),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new MembershipId(7300000000000000005L),
                "a".repeat(64),
                NOW);
    }
}
