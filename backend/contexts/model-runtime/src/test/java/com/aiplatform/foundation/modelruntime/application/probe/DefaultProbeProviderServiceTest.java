package com.aiplatform.foundation.modelruntime.application.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.extension.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.api.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.api.model.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.api.model.ProviderProbeRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultProbeProviderServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");

    @Test
    void 应通过已装配扩展返回稳定探测结果() {
        var service = new DefaultProbeProviderService(
                List.of(successfulProbe()), Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.probe(request(ProviderKind.DETERMINISTIC));

        assertTrue(result.reachable());
        assertEquals(Set.of(ModelCapability.CHAT, ModelCapability.EMBEDDING), result.capabilities());
        assertEquals(NOW, result.probedAt());
    }

    @Test
    void 应拒绝未装配的供应商协议() {
        var service = new DefaultProbeProviderService(
                List.of(successfulProbe()), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(
                UnsupportedProviderException.class,
                () -> service.probe(request(ProviderKind.OPENAI_COMPATIBLE)));
    }

    private static ProviderConnectionProbe successfulProbe() {
        return new ProviderConnectionProbe() {
            @Override
            public ProviderKind providerKind() {
                return ProviderKind.DETERMINISTIC;
            }

            @Override
            public ProviderProbeObservation probe(ProviderProbeRequest request) {
                return new ProviderProbeObservation(
                        true,
                        Set.of(ModelCapability.CHAT, ModelCapability.EMBEDDING),
                        Duration.ZERO,
                        "DETERMINISTIC_OK");
            }
        };
    }

    private static ProviderProbeRequest request(ProviderKind providerKind) {
        return new ProviderProbeRequest(
                new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001")),
                providerKind,
                URI.create("memory://deterministic"),
                "secret://local/demo",
                Duration.ofSeconds(2));
    }
}
