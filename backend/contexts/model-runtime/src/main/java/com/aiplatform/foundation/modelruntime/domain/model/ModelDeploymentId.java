package com.aiplatform.foundation.modelruntime.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 租户内模型部署的稳定标识。
 */
public record ModelDeploymentId(UUID value) {

    public ModelDeploymentId {
        Objects.requireNonNull(value, "模型部署标识不能为空");
    }
}
