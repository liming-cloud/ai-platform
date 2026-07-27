package com.aiplatform.foundation.modelruntime.application.catalog.deployment;

/**
 * 同一模型部署幂等键承载了不同请求。
 */
public final class ModelDeploymentIdempotencyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentIdempotencyConflictException() {
        super("模型部署幂等键已经用于不同请求");
    }
}
