package com.aiplatform.foundation.modelruntime.api.catalog.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 不回显密钥引用的供应商连接公共视图。
 */
public record ProviderConnectionView(
        UUID id,
        String providerKind,
        String displayName,
        URI endpoint,
        String region,
        String authenticationType,
        boolean credentialConfigured,
        String status,
        long version,
        Boolean lastProbeSucceeded,
        String lastProbeCode,
        Instant lastProbedAt,
        Instant createdAt) {

    public ProviderConnectionView {
        Objects.requireNonNull(id, "供应商连接标识不能为空");
        Objects.requireNonNull(providerKind, "供应商类型不能为空");
        Objects.requireNonNull(displayName, "供应商连接名称不能为空");
        Objects.requireNonNull(endpoint, "供应商端点不能为空");
        Objects.requireNonNull(region, "部署区域不能为空");
        Objects.requireNonNull(authenticationType, "认证方式不能为空");
        Objects.requireNonNull(status, "供应商连接状态不能为空");
        Objects.requireNonNull(createdAt, "创建时间不能为空");
    }
}
