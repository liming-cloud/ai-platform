package com.aiplatform.foundation.modelruntime.application.catalog.deployment;

/**
 * 当前租户已经存在同名模型部署。
 */
public final class ModelDeploymentNameConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentNameConflictException() {
        super("当前租户已经存在同名模型部署");
    }
}
