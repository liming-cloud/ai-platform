package com.aiplatform.foundation.modelruntime.domain.provider.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStateException;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProviderConnectionCredentialOperationTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final ProviderConnectionId CONNECTION_ID =
            new ProviderConnectionId(310000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-10T05:00:00Z");

    @Test
    void 建立操作只返回私有引用绑定的草稿连接() {
        var result = new ProviderConnectionCredentialProvisioning().provision(
                TENANT,
                CONNECTION_ID,
                ProviderKind.OPENAI_COMPATIBLE,
                "企业推理服务",
                URI.create("https://models.example.com/v1"),
                "cn-beijing",
                AuthenticationType.API_KEY,
                new CredentialReference("secret://provider-connection/310000000000000001"),
                NOW);

        assertEquals(ProviderConnectionStatus.DRAFT, result.connection().status());
        assertEquals("secret://provider-connection/310000000000000001",
                result.connection().credentialReference().value());
    }

    @Test
    void 轮换操作拒绝停用连接且不得伪造新的成功探测事实() {
        var disabled = connection().disable();

        assertThrows(ProviderConnectionStateException.class, () ->
                new ProviderConnectionCredentialRotation().rotate(
                        disabled,
                        new CredentialReference("secret://provider-connection/310000000000000001")));
    }

    private static ProviderConnection connection() {
        return ProviderConnection.create(
                TENANT,
                CONNECTION_ID,
                ProviderKind.OPENAI_COMPATIBLE,
                "企业推理服务",
                URI.create("https://models.example.com/v1"),
                "cn-beijing",
                AuthenticationType.API_KEY,
                new CredentialReference("secret://provider-connection/old"),
                NOW);
    }
}
