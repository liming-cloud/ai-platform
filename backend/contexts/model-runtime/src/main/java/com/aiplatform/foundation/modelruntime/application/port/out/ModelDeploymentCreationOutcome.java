package com.aiplatform.foundation.modelruntime.application.port.out;

import com.aiplatform.foundation.modelruntime.domain.model.ModelDeployment;
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
