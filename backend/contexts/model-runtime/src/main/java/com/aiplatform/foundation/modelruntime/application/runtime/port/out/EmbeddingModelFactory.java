package com.aiplatform.foundation.modelruntime.application.runtime.port.out;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;

/**
 * 单一供应商协议的嵌入模型构造扩展点。
 */
public interface EmbeddingModelFactory {

    ProviderKind providerKind();

    EmbeddingModelPort create(ModelRuntimeSpecification specification);
}
