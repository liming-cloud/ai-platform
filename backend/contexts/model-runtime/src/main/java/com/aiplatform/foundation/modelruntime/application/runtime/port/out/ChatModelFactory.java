package com.aiplatform.foundation.modelruntime.application.runtime.port.out;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;

/**
 * 单一供应商协议的对话模型构造扩展点。
 */
public interface ChatModelFactory {

    ProviderKind providerKind();

    ChatModelPort create(ModelRuntimeSpecification specification);
}
