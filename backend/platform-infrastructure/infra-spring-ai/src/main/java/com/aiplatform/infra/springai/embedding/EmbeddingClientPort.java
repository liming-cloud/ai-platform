package com.aiplatform.infra.springai.embedding;

import com.aiplatform.infra.springai.embedding.model.EmbeddingRuntimeRequest;

/**
 * 向量模型嵌入端口，隔离模型供应商 SDK 与上层知识向量化流程。
 */
public interface EmbeddingClientPort {
    float[] embed(EmbeddingRuntimeRequest request, String text);
}
