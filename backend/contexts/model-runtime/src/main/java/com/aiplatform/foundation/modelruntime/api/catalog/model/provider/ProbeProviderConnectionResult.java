package com.aiplatform.foundation.modelruntime.api.catalog.model.provider;

import com.aiplatform.foundation.modelruntime.api.model.common.ModelCapability;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 已持久化探测事实及其能力观察。
 */
public record ProbeProviderConnectionResult(
        ProviderConnectionView providerConnection,
        boolean reachable,
        Set<ModelCapability> capabilities,
        Duration elapsed,
        String detailCode,
        Instant probedAt) {

    public ProbeProviderConnectionResult {
        Objects.requireNonNull(providerConnection, "供应商连接不能为空");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "模型能力不能为空"));
        Objects.requireNonNull(elapsed, "探测耗时不能为空");
        Objects.requireNonNull(detailCode, "探测结果编码不能为空");
        Objects.requireNonNull(probedAt, "探测时间不能为空");
    }
}
