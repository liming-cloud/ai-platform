package com.aiplatform.contracts.capability.summary;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SummaryMetricContractsTest {
    @Test
    void platformScopeRequiresPlatformTenantId() {
        assertThatThrownBy(() -> new SummaryMetricQuery(
                SummaryMetricScope.PLATFORM,
                "2001",
                Instant.parse("2026-07-10T00:00:00Z"),
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tenantScopeRejectsPlatformTenantId() {
        assertThatThrownBy(() -> new SummaryMetricQuery(
                SummaryMetricScope.TENANT,
                "0",
                Instant.parse("2026-07-10T00:00:00Z"),
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
