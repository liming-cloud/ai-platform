package com.aiplatform.foundation.modelruntime.application.configuration.port.out;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.security.secret.SecretValue;

/**
 * 将模型配置凭据写入受保护存储；实现不得暴露秘密内容。
 */
public interface ModelConfigurationCredentialWriter {

    void rotate(ModelConfigurationId configurationId, SecretValue apiKey);
}
