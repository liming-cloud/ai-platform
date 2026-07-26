package com.aiplatform.foundation.modelruntime.api.catalog.model;

import com.aiplatform.foundation.modelruntime.api.model.ModelCapability;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 模型部署探测结果。
 */
public record ProbeModelDeploymentResult(
        ModelDeploymentView modelDeployment,
        boolean reachable,
        Set<ModelCapability> capabilities,
        Duration elapsed,
        String detailCode,
        Instant probedAt) {

    public ProbeModelDeploymentResult {
        Objects.requireNonNull(modelDeployment, "模型部署不能为空");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "能力集合不能为空"));
        Objects.requireNonNull(elapsed, "耗时不能为空");
        Objects.requireNonNull(detailCode, "结果分类码不能为空");
        Objects.requireNonNull(probedAt, "探测时间不能为空");
    }
}
