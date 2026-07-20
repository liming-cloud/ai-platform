package com.aiplatform.knowledge.domain.port.modelpolicy;

import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;

/**
 * 知识库上下文模型使用策略端口，由本地模型策略投影实现，禁止同步调用 model-hub 应用服务。
 */
public interface ModelUsagePolicyPort {
    KnowledgeModelUsageDecision decide(KnowledgeModelUsageRequest request);
}
