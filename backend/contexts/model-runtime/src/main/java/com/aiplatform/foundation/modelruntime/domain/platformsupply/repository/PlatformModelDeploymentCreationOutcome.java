package com.aiplatform.foundation.modelruntime.domain.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;

/** 平台模型部署创建或幂等重放的结果。 */
public record PlatformModelDeploymentCreationOutcome(
        PlatformModelDeployment deployment, boolean replayed) implements ValueObject {
    public PlatformModelDeploymentCreationOutcome {
        deployment = Objects.requireNonNull(deployment, "平台模型部署不能为空");
    }
}
