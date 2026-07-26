package com.aiplatform.foundation.trustedquery.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ModelUsage;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueryRunEventTest {

    private static final Instant NOW = Instant.parse("2026-07-27T06:00:00Z");

    @Test
    void 开始与回答事件使用连续序号并保留终态快照() {
        var running = running();
        var answered = running.answer(
                "商品位于一层。\n\n引用：[1]",
                List.of(new QueryCitation(
                        1,
                        UUID.randomUUID(),
                        1,
                        "b".repeat(64),
                        0,
                        "商品位于一层。",
                        0.9)),
                new ModelUsage(8, 4, 12),
                NOW.plusSeconds(1));

        var started = QueryRunEvent.started(running);
        var completed = QueryRunEvent.completed(answered, 2);

        assertEquals(1, started.sequence());
        assertEquals(QueryRunEventType.QUERY_STARTED, started.type());
        assertFalse(started.terminal());
        assertEquals(2, completed.sequence());
        assertEquals(QueryRunEventType.QUERY_ANSWERED, completed.type());
        assertEquals(answered.answer(), completed.answer());
        assertEquals(answered.citations(), completed.citations());
        assertEquals(12, completed.usage().totalTokens());
        assertTrue(completed.terminal());
    }

    @Test
    void 终态事件不能占用开始事件序号() {
        var answered = running().answer(
                "商品位于一层。\n\n引用：[1]",
                List.of(new QueryCitation(
                        1,
                        UUID.randomUUID(),
                        1,
                        "b".repeat(64),
                        0,
                        "商品位于一层。",
                        0.9)),
                new ModelUsage(8, 4, 12),
                NOW.plusSeconds(1));

        assertThrows(
                IllegalArgumentException.class,
                () -> QueryRunEvent.completed(answered, 1));
    }

    private static QueryRun running() {
        return QueryRun.start(
                TenantId.parse("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                new MembershipId(
                        UUID.fromString("40000000-0000-0000-0000-000000000001")),
                "a".repeat(64),
                NOW)
                .acceptQuota(QueryQuotaSnapshot.forAcceptedQuery(
                        NOW,
                        10,
                        1,
                        2,
                        Duration.ofMinutes(2)));
    }
}
