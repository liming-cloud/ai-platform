package com.aiplatform.modelhub.domain.service;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import java.time.Instant;
import java.util.Objects;

/**
 * 模型配置启用策略，保证审批通过且连通性通过后才进入可用状态。
 */
public class ModelConfigActivationPolicy {
    public ModelConfig activateWhenReady(ModelProvider provider, ModelConfig config, Instant updatedAt) {
        Objects.requireNonNull(provider, "模型供应商不能为空");
        Objects.requireNonNull(config, "模型配置不能为空");
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        if (config.status() == ModelConfigStatus.PENDING_APPROVAL
                || config.status() == ModelConfigStatus.REJECTED
                || config.status() == ModelConfigStatus.DEPRECATED) {
            return config;
        }
        if (config.connectivityStatus() != ModelConnectivityStatus.PASSED) {
            return config.status() == ModelConfigStatus.ACTIVE ? config.inactivate(updatedAt) : config;
        }
        if (provider.status() == ModelProviderStatus.ACTIVE
                && config.status() == ModelConfigStatus.INACTIVE) {
            return config.activate(updatedAt);
        }
        return config;
    }

    public ModelConfig inactivateWhenProviderRejected(ModelConfig config, Instant updatedAt) {
        Objects.requireNonNull(config, "模型配置不能为空");
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        if (config.status() != ModelConfigStatus.ACTIVE) {
            return config;
        }
        return config.inactivate(updatedAt);
    }
}
