package com.aiplatform.foundation.modelruntime.api.catalog;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.catalog.model.PublishedChatDeploymentSnapshot;
import java.util.Optional;

/**
 * 向其他限界上下文提供已发布对话部署的最小只读事实。
 */
public interface PublishedChatDeploymentLookup {

    Optional<PublishedChatDeploymentSnapshot> find(
            TenantId tenantId,
            long deploymentId);
}
