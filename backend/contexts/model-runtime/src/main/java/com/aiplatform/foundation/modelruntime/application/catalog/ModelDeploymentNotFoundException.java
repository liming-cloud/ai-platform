package com.aiplatform.foundation.modelruntime.application.catalog;

/**
 * 当前租户内找不到模型部署。
 */
public final class ModelDeploymentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentNotFoundException() {
        super("模型部署不存在");
    }
}
