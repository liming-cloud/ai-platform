package com.aiplatform.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TraceContextTest {
    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void ensureTraceIdNormalizesExplicitTraceId() {
        String traceId = TraceContext.ensureTraceId("  TRACE-REQ-001  ");

        assertThat(traceId).isEqualTo("TRACE-REQ-001");
        assertThat(TraceContext.currentTraceId()).isEqualTo("TRACE-REQ-001");
    }

    @Test
    void ensureTraceIdGeneratesTraceIdForBlankInput() {
        String traceId = TraceContext.ensureTraceId("   ");

        assertThat(traceId).isNotBlank();
        assertThat(TraceContext.currentTraceId()).isEqualTo(traceId);
    }

    @Test
    void currentTraceIdKeepsNormalizedExplicitTraceId() {
        TraceContext.ensureTraceId("\tTRACE-CURRENT-001\n");

        assertThat(TraceContext.currentTraceId()).isEqualTo("TRACE-CURRENT-001");
    }
}
