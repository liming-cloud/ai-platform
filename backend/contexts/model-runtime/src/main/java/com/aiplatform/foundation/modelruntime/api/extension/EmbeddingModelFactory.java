package com.aiplatform.foundation.modelruntime.api.extension;

import com.aiplatform.foundation.modelruntime.api.invocation.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderKind;

/**
 * 单一供应商协议的嵌入模型构造扩展点。
 */
public interface EmbeddingModelFactory {

    ProviderKind providerKind();

    EmbeddingModelPort create(ModelRuntimeSpecification specification);
}
