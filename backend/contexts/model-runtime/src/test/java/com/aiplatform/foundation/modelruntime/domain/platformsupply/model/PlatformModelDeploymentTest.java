package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformModelDeploymentTest {

    private static final Instant NOW = Instant.parse("2026-08-13T04:00:00Z");
    private static final PlatformProviderConnectionId CONNECTION_ID =
            new PlatformProviderConnectionId(310000000000000001L);

    @Test
    void 只有平台范围且未过期的已验证嵌入部署可以发布() {
        var deployment = PlatformModelDeployment.create(
                new PlatformModelDeploymentId(320000000000000001L),
                CONNECTION_ID,
                3,
                "deterministic-embedding",
                "平台确定性嵌入模型",
                ModelType.EMBEDDING,
                Set.of(ModelCapability.EMBEDDING),
                8_192,
                null,
                1_536,
                NOW);

        var published = deployment
                .verify(new PlatformModelCapabilityVerificationId(330000000000000001L),
                        NOW.plusSeconds(86_400))
                .publish(NOW.plusSeconds(1));

        assertEquals(PlatformModelSupplyScope.PLATFORM, published.scope());
        assertEquals(PlatformModelDeploymentStatus.PUBLISHED, published.status());
        assertEquals(2L, published.version());
        assertEquals(3L, published.providerConnectionRevision());
    }

    @Test
    void 草稿或过期验证的平台部署不得发布() {
        var draft = PlatformModelDeployment.create(
                new PlatformModelDeploymentId(320000000000000002L),
                CONNECTION_ID,
                3,
                "deterministic-embedding",
                "平台确定性嵌入模型",
                ModelType.EMBEDDING,
                Set.of(ModelCapability.EMBEDDING),
                8_192,
                null,
                1_536,
                NOW);

        assertThrows(PlatformModelDeploymentStateException.class, () -> draft.publish(NOW));
        assertThrows(
                PlatformModelDeploymentStateException.class,
                () -> draft
                        .verify(new PlatformModelCapabilityVerificationId(330000000000000002L), NOW)
                        .publish(NOW));
    }
}
