package com.aiplatform.foundation.modelruntime.api.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 一次对话调用绑定的不可变模型部署修订。
 */
public record ChatDeploymentRef(UUID deploymentId, long revision) {

    public ChatDeploymentRef {
        Objects.requireNonNull(deploymentId, "对话部署标识不能为空");
        if (revision < 1) {
            throw new IllegalArgumentException("对话部署修订必须大于零");
        }
    }
}
