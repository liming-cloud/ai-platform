package com.aiplatform.foundation.modelruntime.domain.deployment.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentId;
import java.util.Optional;

/**
 * 模型部署持久化端口。
 */
public interface ModelDeploymentRepository {

    ModelDeploymentCreationOutcome create(
            ModelDeploymentCreation creation,
            int tenantLimit);

    Optional<ModelDeployment> findById(
            TenantId tenantId,
            ModelDeploymentId deploymentId);

    ModelDeploymentPage findPage(
            TenantId tenantId,
            long pageNumber,
            long pageSize);

    boolean update(ModelDeployment deployment, long expectedVersion);
}
