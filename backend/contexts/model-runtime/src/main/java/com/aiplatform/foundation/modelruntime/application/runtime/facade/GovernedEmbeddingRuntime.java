package com.aiplatform.foundation.modelruntime.application.runtime.facade;

import com.aiplatform.foundation.modelruntime.application.configuration.dto.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;

/**
 * 使用受控模型配置执行单次嵌入调用，调用结束后不保留 API Key 相关运行时对象。
 */
public interface GovernedEmbeddingRuntime {

    /**
     * 按冻结配置快照执行嵌入；快照、授权或目录状态变化时拒绝调用。
     */
    EmbeddingBatch embed(
            TrustedRequestContext context,
            UsableModelConfigurationSnapshot expected,
            EmbeddingRequest request);
}
