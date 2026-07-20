package com.aiplatform.knowledge.domain.port.modelpolicy.model;

/**
 * 知识库上下文模型使用场景，避免业务层直接拼接 model-hub 策略常量。
 */
public enum KnowledgeModelUseCase {
    RAG_INDEXING,
    RAG_QUERY
}
