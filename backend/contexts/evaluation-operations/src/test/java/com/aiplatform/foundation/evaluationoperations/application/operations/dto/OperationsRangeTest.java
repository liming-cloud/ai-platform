package com.aiplatform.foundation.evaluationoperations.application.operations.dto;

import com.aiplatform.foundation.evaluationoperations.application.operations.dto.overview.OperationsRange;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OperationsRangeTest {

    private static final Instant NOW = Instant.parse("2026-08-16T12:34:56Z");

    @Test
    void 固定范围必须按UTC自然日起点计算并以生成时间结束() {
        assertEquals(Instant.parse("2026-08-16T00:00:00Z"), OperationsRange.DAY_1.periodAt(NOW).from());
        assertEquals(Instant.parse("2026-08-10T00:00:00Z"), OperationsRange.DAY_7.periodAt(NOW).from());
        assertEquals(Instant.parse("2026-07-18T00:00:00Z"), OperationsRange.DAY_30.periodAt(NOW).from());
        assertEquals(NOW, OperationsRange.DAY_30.periodAt(NOW).to());
    }
}
