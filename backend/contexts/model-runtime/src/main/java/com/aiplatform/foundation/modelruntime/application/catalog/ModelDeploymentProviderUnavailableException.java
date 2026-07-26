package com.aiplatform.foundation.modelruntime.application.catalog;

/**
 * 模型部署冻结的供应商连接不可用或修订已经漂移。
 */
public final class ModelDeploymentProviderUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentProviderUnavailableException() {
        super("模型部署冻结的供应商连接不可用");
    }
}
