package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 平台模型发现的安全摘要，不包含地址、凭据或原始供应商响应。 */
public record DiscoverPlatformProviderModelsResult(
        String discoveryId,
        boolean succeeded,
        Set<DiscoveredModel> models,
        String resultCode,
        Duration elapsed,
        Instant discoveredAt,
        Instant expiresAt) {

    public DiscoverPlatformProviderModelsResult {
        discoveryId = Objects.requireNonNull(discoveryId, "平台模型发现标识不能为空");
        models = Set.copyOf(Objects.requireNonNull(models, "发现模型集合不能为空"));
        resultCode = Objects.requireNonNull(resultCode, "模型发现结果编码不能为空");
        elapsed = Objects.requireNonNull(elapsed, "模型发现耗时不能为空");
        discoveredAt = Objects.requireNonNull(discoveredAt, "模型发现时间不能为空");
        if (succeeded && expiresAt == null) {
            throw new IllegalArgumentException("成功模型发现必须包含有效期");
        }
        if (!succeeded && (!models.isEmpty() || expiresAt != null)) {
            throw new IllegalArgumentException("失败模型发现不得返回候选或有效期");
        }
    }
}
