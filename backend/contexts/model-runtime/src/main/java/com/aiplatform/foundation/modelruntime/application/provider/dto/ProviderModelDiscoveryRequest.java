package com.aiplatform.foundation.modelruntime.application.provider.dto;

import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** 厂商发现扩展的受控输入，仅在调用边界携带连接敏感字段。 */
public record ProviderModelDiscoveryRequest(
        TenantId tenantId,
        ProviderKind providerKind,
        URI endpoint,
        CredentialReference credentialReference,
        Duration timeout) {
    public ProviderModelDiscoveryRequest {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        providerKind = Objects.requireNonNull(providerKind, "供应商类型不能为空");
        endpoint = Objects.requireNonNull(endpoint, "供应商地址不能为空");
        credentialReference = Objects.requireNonNull(credentialReference, "密钥引用不能为空");
        timeout = Objects.requireNonNull(timeout, "发现超时不能为空");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("发现超时必须大于零");
        }
    }
}
