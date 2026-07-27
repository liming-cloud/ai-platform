package com.aiplatform.foundation.provider.deterministic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.api.model.ProviderProbeRequest;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.net.URI;
import java.time.Duration;
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
                new TenantId(100000000000000001L),
                ProviderKind.DETERMINISTIC,
                URI.create(endpoint),
                new SecretReference("secret://local/demo"),
                "deterministic-chat",
                java.util.Set.of(),
                Duration.ofSeconds(2));
    }
}
