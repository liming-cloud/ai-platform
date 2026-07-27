package com.aiplatform.foundation.modelruntime.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型目录的租户资源上限。
 */
@ConfigurationProperties("foundation.model-catalog")
public class ModelCatalogProperties {

    private int providerConnectionLimit = 3;
    private int modelDeploymentLimit = 6;

    public int getProviderConnectionLimit() {
        return providerConnectionLimit;
    }

    public void setProviderConnectionLimit(int providerConnectionLimit) {
        if (providerConnectionLimit < 1) {
            throw new IllegalArgumentException("供应商连接上限必须大于零");
        }
        this.providerConnectionLimit = providerConnectionLimit;
    }

    public int getModelDeploymentLimit() {
        return modelDeploymentLimit;
    }

    public void setModelDeploymentLimit(int modelDeploymentLimit) {
        if (modelDeploymentLimit < 1) {
            throw new IllegalArgumentException("模型部署上限必须大于零");
        }
        this.modelDeploymentLimit = modelDeploymentLimit;
    }
}
