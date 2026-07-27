package com.aiplatform.foundation.modelruntime.application.probe.dto;

import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 供应商探测用例的稳定结果，供 HTTP、审计和后续发布门禁共同使用。
 */
public record ProviderProbeResult(
        ProviderKind providerKind,
        boolean reachable,
        Set<ModelCapability> capabilities,
        Duration elapsed,
        String detailCode,
        Instant probedAt) {

    public ProviderProbeResult {
        Objects.requireNonNull(providerKind, "供应商类型不能为空");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "能力集合不能为空"));
        Objects.requireNonNull(elapsed, "耗时不能为空");
        Objects.requireNonNull(detailCode, "结果分类码不能为空");
        Objects.requireNonNull(probedAt, "探测时间不能为空");
    }
}
