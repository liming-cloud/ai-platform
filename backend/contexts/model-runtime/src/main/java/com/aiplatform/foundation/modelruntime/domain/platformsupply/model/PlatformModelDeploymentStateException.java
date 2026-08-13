package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

/** 平台模型部署不满足生命周期转换前置条件时抛出。 */
public final class PlatformModelDeploymentStateException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public PlatformModelDeploymentStateException(String message) {
        super(message);
    }
}
