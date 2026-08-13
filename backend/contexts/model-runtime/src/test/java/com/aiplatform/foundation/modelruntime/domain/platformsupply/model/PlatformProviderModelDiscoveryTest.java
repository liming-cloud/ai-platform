package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryObservation;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformProviderModelDiscoveryTest {
    private static final Instant NOW = Instant.parse("2026-08-13T02:00:00Z");

    @Test
    void 成功平台发现固定平台连接修订而不包含租户标识() {
        var discovery = PlatformProviderModelDiscovery.capture(
                new PlatformProviderModelDiscoveryId(620000000000000001L),
                new PlatformProviderConnectionId(610000000000000001L), 1L,
                new ProviderModelDiscoveryObservation(true, Set.of(new DiscoveredModel(
                        "deterministic-embedding", ModelType.EMBEDDING, Set.of(ModelCapability.EMBEDDING))),
                        "DETERMINISTIC_DISCOVERY_OK", Duration.ZERO, NOW), NOW.plusSeconds(3600));
        assertEquals(1L, discovery.connectionRevision());
        assertEquals(1, discovery.models().size());
    }

    @Test
    void 失败平台发现会清除有效期且不得伪造候选() {
        var discovery = PlatformProviderModelDiscovery.capture(
                new PlatformProviderModelDiscoveryId(620000000000000001L),
                new PlatformProviderConnectionId(610000000000000001L), 1L,
                new ProviderModelDiscoveryObservation(false, Set.of(), "DISCOVERY_FAILED", Duration.ZERO, NOW),
                NOW.plusSeconds(3600));

        assertNull(discovery.expiresAt());
    }
}
