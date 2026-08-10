package com.aiplatform.foundation.modelruntime.domain.provider.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderModelDiscoveryTest {

    private static final Instant NOW = Instant.parse("2026-08-10T03:00:00Z");

    @Test
    void 发现失败只能固化无有效期且无模型的事实() {
        var discovery = ProviderModelDiscovery.capture(
                new ProviderModelDiscoveryId(340000000000000001L),
                new TenantId(100000000000000001L),
                new ProviderConnectionId(310000000000000001L),
                3,
                new ProviderModelDiscoveryObservation(
                        false,
                        Set.of(),
                        "MODEL_DISCOVERY_UNSUPPORTED",
                        Duration.ofMillis(10),
                        NOW),
                NOW.plusSeconds(86_400));

        assertFalse(discovery.succeeded());
        assertTrue(discovery.models().isEmpty());
        assertTrue(discovery.expiresAt() == null);
    }

    @Test
    void 成功发现必须有受模型类型约束的候选并且有效期晚于发生时间() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProviderModelDiscovery.capture(
                        new ProviderModelDiscoveryId(340000000000000001L),
                        new TenantId(100000000000000001L),
                        new ProviderConnectionId(310000000000000001L),
                        3,
                        new ProviderModelDiscoveryObservation(
                                true,
                                Set.of(),
                                "MODEL_DISCOVERY_OK",
                                Duration.ofMillis(10),
                                NOW),
                        NOW.plusSeconds(86_400)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DiscoveredModel(
                        "embedding-model",
                        ModelType.EMBEDDING,
                        Set.of(ModelCapability.CHAT)));
    }

    @Test
    void 成功发现冻结连接修订和候选模型() {
        var discovery = ProviderModelDiscovery.capture(
                new ProviderModelDiscoveryId(340000000000000001L),
                new TenantId(100000000000000001L),
                new ProviderConnectionId(310000000000000001L),
                3,
                new ProviderModelDiscoveryObservation(
                        true,
                        Set.of(new DiscoveredModel(
                                "chat-model",
                                ModelType.CHAT,
                                Set.of(ModelCapability.CHAT))),
                        "MODEL_DISCOVERY_OK",
                        Duration.ofMillis(10),
                        NOW),
                NOW.plusSeconds(86_400));

        assertTrue(discovery.succeeded());
        assertTrue(discovery.contains("chat-model", ModelType.CHAT));
        assertTrue(discovery.expiresAt().isAfter(NOW));
    }
}
