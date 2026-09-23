package com.aiplatform.foundation.modelruntime.application.extension.facade.request;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** 租户供应商模型发现扩展输入。 */
public record ProviderModelDiscoveryRequest(TenantId tenantId, ProviderKind providerKind,
        URI endpoint, String credentialReference, Duration timeout) {
    public ProviderModelDiscoveryRequest {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(providerKind, "供应商类型不能为空");
        Objects.requireNonNull(endpoint, "供应商地址不能为空");
        if (credentialReference == null || credentialReference.isBlank()) {
            throw new IllegalArgumentException("凭据引用不能为空");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("发现超时必须大于零");
        }
    }
}
