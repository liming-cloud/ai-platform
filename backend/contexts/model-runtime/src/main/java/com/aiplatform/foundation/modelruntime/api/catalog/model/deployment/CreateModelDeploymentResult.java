package com.aiplatform.foundation.modelruntime.api.catalog.model.deployment;

import java.util.Objects;

/**
 * 模型部署创建或幂等重放结果。
 */
public record CreateModelDeploymentResult(
        ModelDeploymentView modelDeployment,
        boolean replayed) {

    public CreateModelDeploymentResult {
        Objects.requireNonNull(modelDeployment, "模型部署不能为空");
    }
}
