package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 平台模型能力探测的安全结果，不包含供应商原始响应。 */
public record ProbePlatformModelDeploymentResult(
        PlatformModelDeploymentView deployment, boolean verified, Set<ModelCapability> observedCapabilities,
        Duration elapsed, String resultCode, Instant probedAt) {
    public ProbePlatformModelDeploymentResult {
        deployment = Objects.requireNonNull(deployment, "平台模型部署不能为空");
        observedCapabilities = Set.copyOf(Objects.requireNonNull(observedCapabilities, "实测能力不能为空"));
        elapsed = Objects.requireNonNull(elapsed, "探测耗时不能为空");
        resultCode = Objects.requireNonNull(resultCode, "探测结果编码不能为空");
        probedAt = Objects.requireNonNull(probedAt, "探测时间不能为空");
    }
}
