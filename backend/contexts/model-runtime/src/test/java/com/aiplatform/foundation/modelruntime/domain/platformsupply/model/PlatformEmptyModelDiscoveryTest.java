package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformProviderModelDiscoveryObservation;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformEmptyModelDiscoveryTest {
    @Test
    void 合法账户空列表成功但没有可用于建草稿的候选() {
        var now = Instant.parse("2026-10-21T00:00:00Z");
        var result = assertDoesNotThrow(() -> PlatformProviderModelDiscovery.capture(
                new PlatformProviderModelDiscoveryId(101), new PlatformProviderConnectionId(102), 1,
                new PlatformProviderModelDiscoveryObservation(true, Set.of(), "SILICONFLOW_RERANK_DISCOVERY_OK", Duration.ZERO, now), now.plusSeconds(3600)));
        assertTrue(result.succeeded());
        assertTrue(result.models().isEmpty());
    }
}
