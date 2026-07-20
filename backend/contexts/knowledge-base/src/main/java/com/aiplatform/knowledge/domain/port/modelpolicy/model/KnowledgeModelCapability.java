package com.aiplatform.knowledge.domain.port.modelpolicy.model;

/**
 * 知识库上下文需要的模型能力枚举，用于索引和检索阶段做本地策略判定。
 */
public enum KnowledgeModelCapability {
    CHAT,
    EMBEDDING,
    RERANK
}
