package com.aiplatform.foundation.modelruntime.application.catalog.deployment;

/**
 * 当前租户的模型部署数量已经达到上限。
 */
public final class ModelDeploymentQuotaExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentQuotaExceededException() {
        super("模型部署数量已经达到租户配额");
    }
}
