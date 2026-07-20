package com.aiplatform.knowledge.domain.port.modelpolicy;

import com.aiplatform.knowledge.domain.modelpolicy.KnowledgeModelUsagePolicyProjection;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Optional;

/**
 * 知识库模型使用策略本地投影仓储，禁止回查 model-hub 应用服务。
 */
public interface KnowledgeModelUsagePolicyProjectionRepository {
    Optional<KnowledgeModelUsagePolicyProjection> findByModelConfigId(TenantId tenantId, String modelConfigId);

    KnowledgeModelUsagePolicyProjection save(KnowledgeModelUsagePolicyProjection projection);
}
