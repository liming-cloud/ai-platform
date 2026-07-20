package com.aiplatform.knowledge.domain.port.embedding;

import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.shared.kernel.TenantId;

/**
 * 知识检索查询向量端口，具体模型供应商由组合层或基础设施适配，不允许业务写死模型实现。
 */
public interface KnowledgeQueryEmbeddingPort {
    float[] embedQuery(TenantId tenantId, KnowledgeModelRuntimeDescriptor runtimeDescriptor, String query);
}
