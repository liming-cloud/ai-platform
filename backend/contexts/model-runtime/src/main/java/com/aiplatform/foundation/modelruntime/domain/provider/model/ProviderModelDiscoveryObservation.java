package com.aiplatform.foundation.modelruntime.domain.provider.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 受控发现端口返回的最小观察值，不含供应商原始响应、Endpoint 或凭据。
 */
public record ProviderModelDiscoveryObservation(
        boolean succeeded,
        Set<DiscoveredModel> models,
        String resultCode,
        Duration elapsed,
        Instant discoveredAt) implements ValueObject {

    public ProviderModelDiscoveryObservation {
        models = immutableModels(models);
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

    private static Set<DiscoveredModel> immutableModels(Set<DiscoveredModel> values) {
        values = Objects.requireNonNull(values, "发现模型集合不能为空");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("发现模型集合不能包含空值");
        }
        return Set.copyOf(values);
    }
}
