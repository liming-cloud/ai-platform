package com.aiplatform.foundation.modelruntime.domain.deployment.repository;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeployment;
import java.util.Objects;

/**
 * 模型部署创建或幂等重放结果。
 */
public record ModelDeploymentCreationOutcome(
        ModelDeployment deployment,
        boolean replayed) {

    public ModelDeploymentCreationOutcome {
        Objects.requireNonNull(deployment, "模型部署不能为空");
    }
}
