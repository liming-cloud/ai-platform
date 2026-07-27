package com.aiplatform.foundation.modelruntime.application.catalog.deployment;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.catalog.PublishedChatDeploymentLookup;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.PublishedChatDeploymentSnapshot;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelType;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * 从模型目录投影已发布对话部署的跨上下文快照。
 */
public final class DefaultPublishedChatDeploymentLookup
        implements PublishedChatDeploymentLookup {

    private final ModelDeploymentRepository deployments;

    public DefaultPublishedChatDeploymentLookup(ModelDeploymentRepository deployments) {
        this.deployments = Objects.requireNonNull(deployments, "模型部署仓储不能为空");
    }

    @Override
    public Optional<PublishedChatDeploymentSnapshot> find(
            TenantId tenantId,
            long deploymentId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (deploymentId <= 0) {
            throw new IllegalArgumentException("模型部署标识必须是正整数");
        }
        return deployments
                .findById(tenantId, new ModelDeploymentId(deploymentId))
                .filter(deployment -> deployment.status() == ModelDeploymentStatus.PUBLISHED)
                .filter(deployment -> deployment.modelType() == ModelType.CHAT)
                .map(deployment -> new PublishedChatDeploymentSnapshot(
                        deployment.id().value(),
                        deployment.version(),
                        deployment.contextWindow(),
                        deployment.outputLimit()));
    }
}
