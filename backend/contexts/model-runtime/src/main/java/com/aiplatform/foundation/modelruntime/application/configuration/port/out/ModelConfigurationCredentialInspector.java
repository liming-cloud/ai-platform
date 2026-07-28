package com.aiplatform.foundation.modelruntime.application.configuration.port.out;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;

/**
 * 只暴露模型配置是否具备加密凭据，禁止将任何密文材料带出持久化适配器。
 */
public interface ModelConfigurationCredentialInspector {

    boolean isConfigured(ModelConfigurationId configurationId);
}
