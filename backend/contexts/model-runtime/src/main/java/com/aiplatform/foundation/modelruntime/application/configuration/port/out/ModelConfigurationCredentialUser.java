package com.aiplatform.foundation.modelruntime.application.configuration.port.out;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.util.function.Function;

/**
 * 在已完成授权、配额和预算检查后，于单次动作内使用模型配置的短生命周期凭据。
 */
public interface ModelConfigurationCredentialUser {

    /**
     * 在回调返回或抛出异常后清零解密秘密。
     *
     * @param configurationId 已授权模型配置标识
     * @param action 仅可在当前调用中消费秘密的动作
     * @param <T> 动作返回类型
     * @return 动作执行结果
     */
    <T> T useCredential(
            ModelConfigurationId configurationId,
            Function<SecretValue, T> action);
}
