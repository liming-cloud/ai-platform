package com.aiplatform.foundation.modelruntime.api.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProviderProbeRequestTest {

    @Test
    void 应拒绝空白探测模型编码() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderProbeRequest(
                        new TenantId(100000000000000001L),
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create("https://models.example/v1"),
                        new SecretReference("secret://vault/provider"),
                        " ",
                        java.util.Set.of(),
                        Duration.ofSeconds(2)));
    }
}
