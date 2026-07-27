package com.aiplatform.foundation.modelruntime.application.deployment.dto;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 不包含厂商框架类型的模型部署公共视图。
 */
public record ModelDeploymentView(
        String id,
        String providerConnectionId,
        long providerConnectionRevision,
        String modelCode,
        String displayName,
        String modelType,
        Set<ModelCapability> capabilities,
        int contextWindow,
        Integer outputLimit,
        Integer embeddingDimension,
        String status,
        long version,
        Instant createdAt) {

    public ModelDeploymentView {
        Objects.requireNonNull(id, "模型部署标识不能为空");
        Objects.requireNonNull(providerConnectionId, "供应商连接标识不能为空");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "模型能力不能为空"));
        Objects.requireNonNull(createdAt, "创建时间不能为空");
    }
}
