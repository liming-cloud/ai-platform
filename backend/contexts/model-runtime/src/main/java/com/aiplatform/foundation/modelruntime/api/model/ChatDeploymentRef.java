package com.aiplatform.foundation.modelruntime.api.model;

/**
 * 一次对话调用绑定的不可变模型部署修订。
 */
public record ChatDeploymentRef(long deploymentId, long revision) {

    public ChatDeploymentRef {
        if (deploymentId <= 0 || revision < 1) {
            throw new IllegalArgumentException("对话部署修订必须大于零");
        }
    }
}
