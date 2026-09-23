package com.aiplatform.foundation.provider.deterministic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.application.extension.facade.request.ProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeterministicProviderModelDiscovererTest {

    private final DeterministicProviderModelDiscoverer discoverer =
            new DeterministicProviderModelDiscoverer();

    @Test
    void 内存协议连接应返回固定且类型正确的演示候选模型() {
        var result = discoverer.discover(request("memory://deterministic"));

        assertTrue(result.succeeded());
        assertEquals("DETERMINISTIC_DISCOVERY_OK", result.resultCode());
        assertTrue(result.models().stream().anyMatch(model ->
                model.modelCode().equals("deterministic-chat")
                        && model.modelType() == ModelType.CHAT));
        assertTrue(result.models().stream().anyMatch(model ->
                model.modelCode().equals("deterministic-embedding")
                        && model.modelType() == ModelType.EMBEDDING));
    }

    @Test
    void 非内存协议连接不得伪造可用模型() {
        var result = discoverer.discover(request("https://example.invalid"));

        assertFalse(result.succeeded());
        assertTrue(result.models().isEmpty());
        assertEquals("DETERMINISTIC_ENDPOINT_REJECTED", result.resultCode());
    }

    private static ProviderModelDiscoveryRequest request(String endpoint) {
        return new ProviderModelDiscoveryRequest(
                new TenantId(100000000000000001L),
                ProviderKind.DETERMINISTIC,
                URI.create(endpoint),
                "secret://local/demo",
                Duration.ofSeconds(2));
    }
}
