package com.aiplatform.contracts.capability.knowledge;

/**
 * 知识检索开放能力契约，由 knowledge-base 提供，由 app-studio/agent/workflow 等运行时上下文消费。
 * 契约模块只描述跨上下文稳定协作语义，不承载提供方领域模型或实现细节。
 */
public interface KnowledgeRetrievalCapability {
    KnowledgeRetrievalResponse retrieve(KnowledgeRetrievalRequest request);
}
