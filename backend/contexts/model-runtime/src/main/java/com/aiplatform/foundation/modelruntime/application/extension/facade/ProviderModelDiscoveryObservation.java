package com.aiplatform.foundation.modelruntime.application.extension.facade;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 厂商发现扩展返回的脱敏观察契约。 */
public record ProviderModelDiscoveryObservation(
        boolean succeeded,
        Set<DiscoveredModel> models,
        String resultCode,
        Duration elapsed,
        Instant discoveredAt) {
    public ProviderModelDiscoveryObservation {
        models = Set.copyOf(Objects.requireNonNull(models, "发现模型集合不能为空"));
        if (models.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("发现模型集合不能包含空值");
        }
        Objects.requireNonNull(resultCode, "模型发现结果编码不能为空");
        if (resultCode.isBlank() || !resultCode.equals(resultCode.trim()) || resultCode.length() > 120) {
            throw new IllegalArgumentException("模型发现结果编码格式不正确");
        }
        elapsed = Objects.requireNonNull(elapsed, "模型发现耗时不能为空");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("模型发现耗时不能为负数");
        }
        discoveredAt = Objects.requireNonNull(discoveredAt, "模型发现时间不能为空");
        if (!succeeded && !models.isEmpty()) {
            throw new IllegalArgumentException("失败模型发现不得携带候选模型");
        }
    }

}
