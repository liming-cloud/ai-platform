package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlatformProviderConnectionTest {

    private static final Instant NOW = Instant.parse("2026-08-13T01:00:00Z");

    @Test
    void 平台连接必须固定属于平台范围且不携带租户标识() {
        var connection = PlatformProviderConnection.create(
                new PlatformProviderConnectionId(310000000000000001L),
                ProviderKind.OPENAI_COMPATIBLE,
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.OPENAI_MODELS_API,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                "平台确定性模型连接",
                URI.create("https://models.example.com/v1"),
                "本地研发",
                AuthenticationType.API_KEY,
                new CredentialReference("secret://platform-provider-connection/310000000000000001"),
                NOW);

        assertEquals(PlatformModelSupplyScope.PLATFORM, connection.scope());
        assertEquals(ProviderConnectionStatus.DRAFT, connection.status());
        assertEquals(0L, connection.version());
        assertEquals(
                "secret://platform-provider-connection/310000000000000001",
                connection.credentialReference().value());
    }

    @Test
    void 平台连接轮换凭据必须清除成功探测而不能复用旧修订() {
        var rotated = PlatformProviderConnection.create(
                new PlatformProviderConnectionId(310000000000000001L),
                ProviderKind.OPENAI_COMPATIBLE,
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.OPENAI_MODELS_API,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                "平台确定性模型连接",
                URI.create("https://models.example.com/v1"),
                "本地研发",
                AuthenticationType.API_KEY,
                new CredentialReference("secret://platform-provider-connection/310000000000000001"),
                NOW)
                .recordSuccessfulProbe("OPENAI_COMPATIBLE_OK", NOW)
                .rotateCredential(new CredentialReference(
                        "secret://platform-provider-connection/310000000000000001"));

        assertEquals(PlatformModelSupplyScope.PLATFORM, rotated.scope());
        assertEquals(ProviderConnectionStatus.DRAFT, rotated.status());
        assertEquals(2L, rotated.version());
        assertEquals(null, rotated.lastProbeSucceeded());
        assertEquals(null, rotated.lastProbedAt());
    }

    @Test
    void 平台凭据引用不得误用租户连接命名空间() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlatformProviderConnection.create(
                        new PlatformProviderConnectionId(310000000000000001L),
                        ProviderKind.OPENAI_COMPATIBLE,
                        com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.OPENAI_MODELS_API,
                        new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                        "平台确定性模型连接",
                        URI.create("https://models.example.com/v1"),
                        "本地研发",
                        AuthenticationType.API_KEY,
                        new CredentialReference("secret://provider-connection/310000000000000001"),
                        NOW));
    }
}
