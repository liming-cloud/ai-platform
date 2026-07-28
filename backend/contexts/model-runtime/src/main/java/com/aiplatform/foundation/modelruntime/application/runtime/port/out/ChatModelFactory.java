package com.aiplatform.foundation.modelruntime.application.runtime.port.out;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.util.Objects;

/**
 * 单一供应商协议的对话模型构造扩展点。
 */
public interface ChatModelFactory {

    ProviderKind providerKind();

    ChatModelPort create(ModelRuntimeSpecification specification);

    /**
     * 使用当前调用持有的短生命周期凭据创建模型；默认仅用于不消费凭据的本地扩展。
     */
    default ChatModelPort create(
            ModelRuntimeSpecification specification,
            SecretValue credential) {
        Objects.requireNonNull(credential, "模型凭据不能为空");
        return create(specification);
    }
}
