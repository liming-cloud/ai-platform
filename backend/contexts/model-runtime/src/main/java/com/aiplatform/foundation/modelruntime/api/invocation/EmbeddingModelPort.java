package com.aiplatform.foundation.modelruntime.api.invocation;

import com.aiplatform.foundation.modelruntime.api.model.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingRequest;

/**
 * 知识摄取与查询共同使用的框架无关嵌入契约。
 */
public interface EmbeddingModelPort {

    EmbeddingBatch embed(EmbeddingRequest request);
}
