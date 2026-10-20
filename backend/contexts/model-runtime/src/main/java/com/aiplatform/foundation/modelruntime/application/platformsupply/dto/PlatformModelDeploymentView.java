package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailability;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailabilityNextStep;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformModelDeploymentInputsView;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 不返回端点、密钥引用或探测原文的平台模型部署安全摘要。 */
public record PlatformModelDeploymentView(
        String id, String providerConnectionId, long providerConnectionRevision, String modelCode,
        String displayName, String modelType, Set<ModelCapability> capabilities, int contextWindow,
        Integer outputLimit, Integer embeddingDimension, Instant verificationExpiresAt, String status,
        ModelDeploymentAvailability availability, ModelDeploymentAvailabilityNextStep nextStep,
        long version, Instant createdAt, String connectionDisplayName,
        PlatformModelDeploymentInputsView inputs, Instant verifiedAt) {
    public PlatformModelDeploymentView {
        id = Objects.requireNonNull(id, "平台模型部署标识不能为空");
        providerConnectionId = Objects.requireNonNull(providerConnectionId, "平台供应商连接标识不能为空");
        modelCode = Objects.requireNonNull(modelCode, "模型代码不能为空");
        displayName = Objects.requireNonNull(displayName, "平台模型部署名称不能为空");
        modelType = Objects.requireNonNull(modelType, "模型类型不能为空");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "模型能力不能为空"));
        status = Objects.requireNonNull(status, "平台模型部署状态不能为空");
        availability = Objects.requireNonNull(availability, "可用性不能为空");
        nextStep = Objects.requireNonNull(nextStep, "建议下一步不能为空");
        createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }
}
