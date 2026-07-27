package com.aiplatform.foundation.modelruntime.api.invocation;

import com.aiplatform.foundation.kernel.tenant.TenantId;

/**
 * 按可信租户和已发布部署解析模型运行时对象。
 */
public interface ModelRuntimeResolver {

    ChatModelPort resolveChat(TenantId tenantId, long deploymentId);

    EmbeddingModelPort resolveEmbedding(TenantId tenantId, long deploymentId);

    void evictProvider(TenantId tenantId, long providerConnectionId);

    void evictDeployment(TenantId tenantId, long deploymentId);
}
