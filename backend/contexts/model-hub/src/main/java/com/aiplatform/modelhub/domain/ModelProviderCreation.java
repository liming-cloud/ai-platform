package com.aiplatform.modelhub.domain;

import java.util.Objects;

/**
 * 模型供应商创建的领域结果，同时返回聚合根和需要审批的配置变更。
 */
public record ModelProviderCreation(ModelProvider provider, ModelConfigChange configChange) {
    public ModelProviderCreation {
        Objects.requireNonNull(provider, "模型供应商不能为空");
        Objects.requireNonNull(configChange, "模型配置变更不能为空");
    }
}
