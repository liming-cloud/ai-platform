package com.aiplatform.foundation.modelruntime.api.extension;

import com.aiplatform.foundation.modelruntime.api.invocation.ChatModelPort;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderKind;

/**
 * 单一供应商协议的对话模型构造扩展点。
 */
public interface ChatModelFactory {

    ProviderKind providerKind();

    ChatModelPort create(ModelRuntimeSpecification specification);
}
