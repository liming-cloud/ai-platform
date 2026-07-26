package com.aiplatform.foundation.modelruntime.application.catalog;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.catalog.PublishedEmbeddingDeploymentLookup;
import com.aiplatform.foundation.modelruntime.api.catalog.model.PublishedEmbeddingDeploymentSnapshot;
import com.aiplatform.foundation.modelruntime.api.model.ModelType;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 从模型目录投影已发布嵌入部署的跨上下文快照。
 */
public final class DefaultPublishedEmbeddingDeploymentLookup
        implements PublishedEmbeddingDeploymentLookup {

    private final ModelDeploymentRepository deployments;

    public DefaultPublishedEmbeddingDeploymentLookup(
            ModelDeploymentRepository deployments) {
        this.deployments = Objects.requireNonNull(deployments, "模型部署仓储不能为空");
    }

    @Override
    public Optional<PublishedEmbeddingDeploymentSnapshot> find(
            TenantId tenantId,
            UUID deploymentId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(deploymentId, "模型部署标识不能为空");
        return deployments
                .findById(tenantId, new ModelDeploymentId(deploymentId))
                .filter(deployment -> deployment.status() == ModelDeploymentStatus.PUBLISHED)
                .filter(deployment -> deployment.modelType() == ModelType.EMBEDDING)
                .map(deployment -> new PublishedEmbeddingDeploymentSnapshot(
                        deployment.id().value(),
                        deployment.version(),
                        deployment.embeddingDimension()));
    }
}
