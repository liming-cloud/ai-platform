package com.aiplatform.foundation.modelruntime.application.provider.dto;

import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** 不含连接秘密和原始响应的模型发现结果。 */
public record DiscoverProviderModelsResult(
        String discoveryId,
        boolean succeeded,
        Set<DiscoveredModel> models,
        String resultCode,
        Duration elapsed,
        Instant discoveredAt,
        Instant expiresAt) {
}
