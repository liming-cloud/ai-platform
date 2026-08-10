package com.aiplatform.foundation.modelruntime.domain.deployment.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 一次受控模型探测返回的领域观察值，不携带供应商原始响应或连接敏感信息。
 */
public record ModelCapabilityObservation(
        boolean reachable,
        Set<ModelCapability> capabilities,
        String detailCode,
        Duration elapsed,
        Instant probedAt) implements ValueObject {

    public ModelCapabilityObservation {
        capabilities = immutableCapabilities(capabilities);
        Objects.requireNonNull(detailCode, "探测结果编码不能为空");
        if (detailCode.isBlank() || !detailCode.equals(detailCode.trim()) || detailCode.length() > 120) {
            throw new IllegalArgumentException("探测结果编码格式不正确");
        }
        elapsed = Objects.requireNonNull(elapsed, "探测耗时不能为空");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("探测耗时不能为负数");
        }
        probedAt = Objects.requireNonNull(probedAt, "探测时间不能为空");
    }

    private static Set<ModelCapability> immutableCapabilities(Set<ModelCapability> values) {
        values = Objects.requireNonNull(values, "实测能力不能为空");
        for (var value : values) {
            if (value == null) {
                throw new IllegalArgumentException("实测能力不能包含空值");
            }
        }
        return Set.copyOf(values);
    }
}
