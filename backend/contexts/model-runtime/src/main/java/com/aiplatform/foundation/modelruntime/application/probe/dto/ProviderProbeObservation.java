package com.aiplatform.foundation.modelruntime.application.probe.dto;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * 厂商扩展返回的受限探测观测值。
 *
 * <p>错误只允许使用稳定分类码，不接受厂商原始响应，避免凭据和提示内容泄漏。</p>
 */
public record ProviderProbeObservation(
        boolean reachable,
        Set<ModelCapability> capabilities,
        Duration elapsed,
        String detailCode) {

    public ProviderProbeObservation {
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "能力集合不能为空"));
        Objects.requireNonNull(elapsed, "耗时不能为空");
        Objects.requireNonNull(detailCode, "结果分类码不能为空");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("探测耗时不能为负数");
        }
    }
}
