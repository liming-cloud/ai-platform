package com.aiplatform.foundation.modelruntime.application.configuration.service;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import java.util.Objects;

/**
 * 模型配置的应用层读取结果，只保留可安全返回的凭据存在状态。
 */
public record ModelConfigurationReadResult(
        ModelConfiguration configuration,
        boolean credentialConfigured) {

    public ModelConfigurationReadResult {
        Objects.requireNonNull(configuration, "模型配置不能为空");
    }
}
