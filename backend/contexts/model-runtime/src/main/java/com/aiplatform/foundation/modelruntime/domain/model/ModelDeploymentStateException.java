package com.aiplatform.foundation.modelruntime.domain.model;

/**
 * 模型部署状态不允许执行当前动作。
 */
public final class ModelDeploymentStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentStateException(String message) {
        super(message);
    }
}
