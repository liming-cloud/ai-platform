package com.aiplatform.foundation.modelruntime.application.runtime.facade;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;

/**
 * 知识摄取与查询共同使用的框架无关嵌入契约。
 */
public interface EmbeddingModelPort {

    EmbeddingBatch embed(EmbeddingRequest request);
}
