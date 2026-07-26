package com.aiplatform.foundation.modelruntime.api.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProviderProbeRequestTest {

    @Test
    void 应拒绝空白探测模型编码() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderProbeRequest(
                        new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001")),
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create("https://models.example/v1"),
                        new SecretReference("secret://vault/provider"),
                        " ",
                        java.util.Set.of(),
                        Duration.ofSeconds(2)));
    }
}
