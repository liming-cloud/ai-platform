package com.aiplatform.foundation.modelruntime.application.provider.dto;

import java.time.Duration;
import java.time.Instant;

/** 面向控制台的发现历史安全摘要，不回显候选、Endpoint、凭据或原始响应。 */
public record ProviderModelDiscoveryView(
        String discoveryId,
        long providerConnectionRevision,
        boolean succeeded,
        int candidateCount,
        String resultCode,
        Duration elapsed,
        Instant discoveredAt,
        Instant expiresAt,
        String nextStep) {
}
