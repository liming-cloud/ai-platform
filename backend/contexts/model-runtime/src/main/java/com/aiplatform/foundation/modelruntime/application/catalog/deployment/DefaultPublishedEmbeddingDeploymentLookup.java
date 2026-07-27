package com.aiplatform.foundation.modelruntime.application.catalog.deployment;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.catalog.PublishedEmbeddingDeploymentLookup;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.PublishedEmbeddingDeploymentSnapshot;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelType;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentStatus;
import java.util.Objects;
import java.util.Optional;

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
            long deploymentId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (deploymentId <= 0) {
            throw new IllegalArgumentException("模型部署标识必须是正整数");
        }
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
