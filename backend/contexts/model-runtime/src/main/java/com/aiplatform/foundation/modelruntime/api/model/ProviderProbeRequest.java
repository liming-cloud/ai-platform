package com.aiplatform.foundation.modelruntime.api.model;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * 供应商连接探测的公共输入。
 *
 * <p>该契约只允许传递密钥引用。真实凭据由基础设施适配器在调用边界按需读取，
 * 不得进入公共接口、数据库或审计记录。</p>
 */
public record ProviderProbeRequest(
        TenantId tenantId,
        ProviderKind providerKind,
        URI endpoint,
        String credentialReference,
        Duration timeout) {

    public ProviderProbeRequest {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(providerKind, "供应商类型不能为空");
        Objects.requireNonNull(endpoint, "供应商地址不能为空");
        Objects.requireNonNull(credentialReference, "密钥引用不能为空");
        Objects.requireNonNull(timeout, "探测超时不能为空");
        if (!credentialReference.startsWith("secret://")) {
            throw new IllegalArgumentException("密钥必须使用 secret:// 引用");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("探测超时必须大于零");
        }
    }
}
