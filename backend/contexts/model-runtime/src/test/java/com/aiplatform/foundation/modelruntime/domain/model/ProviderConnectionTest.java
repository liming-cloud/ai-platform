package com.aiplatform.foundation.modelruntime.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProviderConnectionTest {

    private static final TenantId TENANT_ID =
            new TenantId(100000000000000001L);
    private static final ProviderConnectionId CONNECTION_ID =
            new ProviderConnectionId(UUID.fromString("41000000-0000-0000-0000-000000000001"));
    private static final Instant NOW = Instant.parse("2026-07-27T04:00:00Z");

    @Test
    void 新连接应处于草稿且只保存密钥引用() {
        var connection = createConnection();

        assertEquals(ProviderConnectionStatus.DRAFT, connection.status());
        assertEquals(0, connection.version());
        assertEquals("secret://demo/openai", connection.credentialReference().value());
    }

    @Test
    void 探测成功后应形成可审计的已验证修订() {
        var verified = createConnection().recordSuccessfulProbe("OPENAI_COMPATIBLE_OK", NOW);

        assertEquals(ProviderConnectionStatus.VERIFIED, verified.status());
        assertEquals(1, verified.version());
        assertEquals(true, verified.lastProbeSucceeded());
        assertEquals("OPENAI_COMPATIBLE_OK", verified.lastProbeCode());
        assertEquals(NOW, verified.lastProbedAt());
    }

    @Test
    void 探测失败应保留失败事实但不得验证连接() {
        var failed = createConnection().recordFailedProbe(
                "OPENAI_COMPATIBLE_AUTHENTICATION_FAILED",
                NOW);

        assertEquals(ProviderConnectionStatus.DRAFT, failed.status());
        assertEquals(1, failed.version());
        assertEquals(false, failed.lastProbeSucceeded());
        assertEquals("OPENAI_COMPATIBLE_AUTHENTICATION_FAILED", failed.lastProbeCode());
    }

    @Test
    void 已停用连接不得重新验证() {
        var disabled = createConnection().disable();

        assertThrows(
                ProviderConnectionStateException.class,
                () -> disabled.recordSuccessfulProbe("OPENAI_COMPATIBLE_OK", NOW));
    }

    @Test
    void 连接名称不得包含换行或控制字符() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProviderConnection.create(
                        TENANT_ID,
                        CONNECTION_ID,
                        ProviderKind.OPENAI_COMPATIBLE,
                        "生产\n连接",
                        URI.create("https://models.example.com/v1"),
                        "华东",
                        AuthenticationType.API_KEY,
                        new SecretReference("secret://demo/openai"),
                        NOW));
    }

    private static ProviderConnection createConnection() {
        return ProviderConnection.create(
                TENANT_ID,
                CONNECTION_ID,
                ProviderKind.OPENAI_COMPATIBLE,
                "生产模型连接",
                URI.create("https://models.example.com/v1"),
                "华东",
                AuthenticationType.API_KEY,
                new SecretReference("secret://demo/openai"),
                NOW);
    }
}
