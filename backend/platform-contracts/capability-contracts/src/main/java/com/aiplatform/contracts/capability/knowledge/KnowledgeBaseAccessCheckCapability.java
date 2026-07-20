package com.aiplatform.contracts.capability.knowledge;

/**
 * KnowledgeOps 面向运行时上下文开放的知识库绑定可用性检查契约。
 */
public interface KnowledgeBaseAccessCheckCapability {
    KnowledgeBaseAccessCheckResponse check(KnowledgeBaseAccessCheckRequest request);
}
