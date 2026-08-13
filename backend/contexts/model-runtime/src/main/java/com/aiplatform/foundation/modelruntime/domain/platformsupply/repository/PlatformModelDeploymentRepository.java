package com.aiplatform.foundation.modelruntime.domain.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import java.util.Optional;

/** 平台模型部署持久化端口，不接受租户范围参数。 */
public interface PlatformModelDeploymentRepository {
    PlatformModelDeploymentCreationOutcome create(PlatformModelDeploymentCreation creation);
    Optional<PlatformModelDeployment> findById(PlatformModelDeploymentId id);
    PlatformModelDeploymentPage findPage(long pageNumber, long pageSize);
    boolean update(PlatformModelDeployment deployment, long expectedVersion);
}
