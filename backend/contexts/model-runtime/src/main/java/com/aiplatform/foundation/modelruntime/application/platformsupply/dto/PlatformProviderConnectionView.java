package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import java.time.Instant;
import java.util.Objects;

/** 不返回端点、凭据引用或探测原始响应的平台连接安全摘要。 */
public record PlatformProviderConnectionView(
        String id,
        String providerKind,
        String displayName,
        String region,
        String authenticationType,
        boolean credentialConfigured,
        String status,
        long version,
        Boolean lastProbeSucceeded,
        String lastProbeCode,
        Instant lastProbedAt,
        Instant createdAt) {

    public PlatformProviderConnectionView {
        Objects.requireNonNull(id, "平台供应商连接标识不能为空");
        Objects.requireNonNull(providerKind, "供应商类型不能为空");
        Objects.requireNonNull(displayName, "平台供应商连接名称不能为空");
        Objects.requireNonNull(region, "部署区域不能为空");
        Objects.requireNonNull(authenticationType, "认证方式不能为空");
        Objects.requireNonNull(status, "供应商连接状态不能为空");
        Objects.requireNonNull(createdAt, "创建时间不能为空");
    }
}
