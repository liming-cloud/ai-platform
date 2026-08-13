package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import java.util.Objects;

/** 平台模型部署创建或幂等重放的安全结果。 */
public record CreatePlatformModelDeploymentResult(
        PlatformModelDeploymentView deployment, boolean replayed) {
    public CreatePlatformModelDeploymentResult {
        deployment = Objects.requireNonNull(deployment, "平台模型部署不能为空");
    }
}
