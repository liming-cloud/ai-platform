package com.aiplatform.foundation.modelruntime.api.catalog;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.catalog.model.PublishedEmbeddingDeploymentSnapshot;
import java.util.Optional;
import java.util.UUID;

/**
 * 向其他限界上下文提供已发布嵌入部署的最小只读事实。
 */
public interface PublishedEmbeddingDeploymentLookup {

    Optional<PublishedEmbeddingDeploymentSnapshot> find(
            TenantId tenantId,
            UUID deploymentId);
}
