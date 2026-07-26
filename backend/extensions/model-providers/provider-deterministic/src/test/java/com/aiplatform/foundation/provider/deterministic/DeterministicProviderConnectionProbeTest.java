package com.aiplatform.foundation.provider.deterministic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.api.model.ProviderProbeRequest;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicProviderConnectionProbeTest {

    private final DeterministicProviderConnectionProbe probe =
            new DeterministicProviderConnectionProbe();

    @Test
    void 应接受内存协议地址() {
        assertTrue(probe.probe(request("memory://deterministic")).reachable());
    }

    @Test
    void 应拒绝非内存协议地址() {
        assertFalse(probe.probe(request("https://example.invalid")).reachable());
    }

    private static ProviderProbeRequest request(String endpoint) {
        return new ProviderProbeRequest(
                new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001")),
                ProviderKind.DETERMINISTIC,
                URI.create(endpoint),
                "secret://local/demo",
                Duration.ofSeconds(2));
    }
}
