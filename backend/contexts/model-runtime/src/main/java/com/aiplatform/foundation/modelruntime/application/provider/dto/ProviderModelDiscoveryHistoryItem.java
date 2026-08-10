package com.aiplatform.foundation.modelruntime.application.provider.dto;

import java.time.Instant;
import java.util.Objects;

/** 存储层返回的发现历史安全摘要行，不含候选明细或连接敏感字段。 */
public record ProviderModelDiscoveryHistoryItem(
        String discoveryId,
        long providerConnectionRevision,
        boolean succeeded,
        int candidateCount,
        String resultCode,
        long elapsedMillis,
        Instant discoveredAt,
        Instant expiresAt) {

    public ProviderModelDiscoveryHistoryItem {
        discoveryId = Objects.requireNonNull(discoveryId, "模型发现标识不能为空");
        if (providerConnectionRevision < 0 || candidateCount < 0 || elapsedMillis < 0) {
            throw new IllegalArgumentException("模型发现历史摘要数值不能为负数");
        }
        resultCode = Objects.requireNonNull(resultCode, "模型发现结果编码不能为空");
        discoveredAt = Objects.requireNonNull(discoveredAt, "模型发现时间不能为空");
        if (succeeded && expiresAt == null) {
            throw new IllegalArgumentException("成功模型发现摘要必须包含失效时间");
        }
        if (!succeeded && (candidateCount != 0 || expiresAt != null)) {
            throw new IllegalArgumentException("失败模型发现摘要不得包含候选或失效时间");
        }
    }
}
