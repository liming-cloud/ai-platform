package com.aiplatform.foundation.evaluationoperations.domain.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReportingPeriodTest {

    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @Test
    void 未指定范围时应使用当前协调世界时自然日() {
        var period = ReportingPeriod.requestedOrCurrentDay(null, null, NOW);

        assertEquals(Instant.parse("2026-07-27T00:00:00Z"), period.from());
        assertEquals(Instant.parse("2026-07-28T00:00:00Z"), period.to());
    }

    @Test
    void 应拒绝缺少端点和超过三十一天的范围() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportingPeriod.requestedOrCurrentDay(NOW, null, NOW));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReportingPeriod(
                        NOW,
                        NOW.plus(Duration.ofDays(31)).plusSeconds(1)));
    }

    @Test
    void 配额策略应拒绝非正数上限() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationsQuotaPolicy(0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationsQuotaPolicy(1, 0));
    }
}
