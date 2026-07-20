package com.aiplatform.modelhub.domain.modelpolicy.model;

/**
 * 模型使用场景枚举，用于模型策略和业务本地投影保持统一语义。
 */
public enum ModelUseCase {
    RAG_INDEXING,
    RAG_QUERY,
    CHAT_COMPLETION,
    EMBEDDING,
    RERANK,
    EVALUATION
}
