package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import static org.junit.jupiter.api.Assertions.*;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.publication.PlatformProviderConnectionPublication;
import com.aiplatform.foundation.modelruntime.domain.provider.model.*;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlatformProviderConnectionPublicationTest {
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    private static final Duration VALIDITY = Duration.ofHours(24);

    @Test void 发布只冻结有效探测且不改变连接修订或验证状态() {
        var verified = draft().recordSuccessfulProbe("CONNECTION_OK", NOW);
        var publication = publish(verified, NOW);
        assertEquals(verified.id(), publication.connectionId());
        assertEquals(verified.version(), publication.connectionRevision());
        assertEquals(1L, publication.revision());
        assertEquals(NOW, publication.probedAt());
        assertEquals(NOW, publication.publishedAt());
        assertEquals(101L, publication.publishedBySubjectId());
        assertEquals(ProviderConnectionStatus.VERIFIED, verified.status());
        assertEquals(1L, verified.version());
    }

    @Test void 草稿失败与停用连接不能发布() {
        assertThrows(ProviderConnectionStateException.class, () -> publish(draft(), NOW));
        assertThrows(ProviderConnectionStateException.class,
                () -> publish(draft().recordFailedProbe("AUTHENTICATION_FAILED", NOW), NOW));
        assertThrows(ProviderConnectionStateException.class,
                () -> publish(draft().recordSuccessfulProbe("CONNECTION_OK", NOW).disable(), NOW));
    }

    @Test void 有效期终点与未来探测均失败关闭() {
        var verified = draft().recordSuccessfulProbe("CONNECTION_OK", NOW);
        assertDoesNotThrow(() -> publish(verified, NOW.plus(VALIDITY).minusNanos(1)));
        assertThrows(ProviderConnectionStateException.class, () -> publish(verified, NOW.plus(VALIDITY)));
        assertThrows(ProviderConnectionStateException.class, () -> publish(verified, NOW.minusSeconds(1)));
    }

    @Test void 轮换后不能使用旧验证建立新发布() {
        var rotated = draft().recordSuccessfulProbe("CONNECTION_OK", NOW)
                .rotateCredential(new CredentialReference("secret://platform-provider-connection/310000000000000001"));
        assertThrows(ProviderConnectionStateException.class, () -> publish(rotated, NOW));
    }

    private static PlatformProviderConnectionPublication publish(PlatformProviderConnection connection, Instant now) {
        return PlatformProviderConnectionPublication.publish(320000000000000001L, 1L, connection, 101L, now, VALIDITY);
    }

    private static PlatformProviderConnection draft() {
        return PlatformProviderConnection.create(
                new PlatformProviderConnectionId(310000000000000001L),
                ProviderKind.DETERMINISTIC,
                PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                "合成连接",
                URI.create("memory://synthetic"),
                "local",
                AuthenticationType.API_KEY,
                new CredentialReference("secret://platform-provider-connection/310000000000000001"),
                NOW);
    }
}
