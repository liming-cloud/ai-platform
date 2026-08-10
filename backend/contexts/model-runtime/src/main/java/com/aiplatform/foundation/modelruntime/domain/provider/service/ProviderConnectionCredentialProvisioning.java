package com.aiplatform.foundation.modelruntime.domain.provider.service;

import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * 将新连接与其仅限内部使用的凭据引用绑定为一个领域操作结果。
 */
public final class ProviderConnectionCredentialProvisioning {

    public ProviderConnectionCredentialProvisioningResult provision(
            TenantId tenantId,
            ProviderConnectionId connectionId,
            ProviderKind providerKind,
            String displayName,
            URI endpoint,
            String region,
            AuthenticationType authenticationType,
            CredentialReference credentialReference,
            Instant createdAt) {
        return new ProviderConnectionCredentialProvisioningResult(
                ProviderConnection.create(
                        tenantId,
                        connectionId,
                        providerKind,
                        displayName,
                        endpoint,
                        region,
                        authenticationType,
                        Objects.requireNonNull(credentialReference, "私有凭据引用不能为空"),
                        createdAt));
    }
}
