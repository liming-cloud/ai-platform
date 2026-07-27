package com.aiplatform.foundation.modelruntime.application.port.out;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentId;
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

    ModelDeploymentSlice findPage(
            TenantId tenantId,
            String cursor,
            int limit);

    boolean update(ModelDeployment deployment, long expectedVersion);
}
