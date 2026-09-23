package com.aiplatform.foundation.modelruntime.application.extension.facade;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** 扩展探测结果只允许稳定分类码，不携带厂商原始响应。 */
public record ProviderProbeObservation(boolean reachable, Set<ModelCapability> capabilities,
        Duration elapsed, String detailCode) {
    public ProviderProbeObservation {
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "能力集合不能为空"));
        if (elapsed == null || elapsed.isNegative()) {
            throw new IllegalArgumentException("探测耗时不能为负数");
        }
        Objects.requireNonNull(detailCode, "结果分类码不能为空");
    }
    public static ProviderProbeObservation of(boolean reachable, Set<ModelCapability> capabilities,
            Duration elapsed, String detailCode) {
        return new ProviderProbeObservation(reachable, capabilities, elapsed, detailCode);
    }
}
