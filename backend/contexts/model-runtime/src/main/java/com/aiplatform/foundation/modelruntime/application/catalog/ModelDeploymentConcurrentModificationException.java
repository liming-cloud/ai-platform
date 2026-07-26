package com.aiplatform.foundation.modelruntime.application.catalog;

/**
 * 模型部署已被其他请求更新。
 */
public final class ModelDeploymentConcurrentModificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentConcurrentModificationException() {
        super("模型部署版本已经变化");
    }
}
