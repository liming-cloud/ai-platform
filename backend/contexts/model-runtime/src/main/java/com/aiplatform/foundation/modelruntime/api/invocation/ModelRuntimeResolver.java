package com.aiplatform.foundation.modelruntime.api.invocation;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import java.util.UUID;

/**
 * 按可信租户和已发布部署解析模型运行时对象。
 */
public interface ModelRuntimeResolver {

    ChatModelPort resolveChat(TenantId tenantId, UUID deploymentId);

    EmbeddingModelPort resolveEmbedding(TenantId tenantId, UUID deploymentId);

    void evictProvider(TenantId tenantId, UUID providerConnectionId);

    void evictDeployment(TenantId tenantId, UUID deploymentId);
}
