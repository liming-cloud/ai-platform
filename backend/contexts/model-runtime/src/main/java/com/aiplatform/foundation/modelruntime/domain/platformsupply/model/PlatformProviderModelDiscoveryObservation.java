package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 平台发现适配器返回的脱敏观察值，不含供应商原始响应、Endpoint 或秘密。 */
public record PlatformProviderModelDiscoveryObservation(
        boolean succeeded,
        Set<PlatformDiscoveredModelCandidate> candidates,
        String resultCode,
        Duration elapsed,
        Instant discoveredAt) implements ValueObject {

    public PlatformProviderModelDiscoveryObservation {
        candidates = Set.copyOf(Objects.requireNonNull(candidates, "平台发现候选不能为空"));
        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("平台发现候选不能包含空值");
        }
        Objects.requireNonNull(resultCode, "平台模型发现结果编码不能为空");
        if (resultCode.isBlank() || !resultCode.equals(resultCode.trim()) || resultCode.length() > 120) {
            throw new IllegalArgumentException("平台模型发现结果编码格式不正确");
        }
        elapsed = Objects.requireNonNull(elapsed, "平台模型发现耗时不能为空");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("平台模型发现耗时不能为负数");
        }
        discoveredAt = Objects.requireNonNull(discoveredAt, "平台模型发现时间不能为空");
        if (!succeeded && !candidates.isEmpty()) {
            throw new IllegalArgumentException("失败平台模型发现不得携带候选");
        }
    }
}
