package com.aiplatform.foundation.controlplane.web.modelcatalog;

import com.aiplatform.foundation.modelruntime.api.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.model.ModelType;
import java.util.Set;
import java.util.UUID;

/**
 * 创建模型部署的 HTTP 请求。
 */
public record ModelDeploymentRequest(
        UUID providerConnectionId,
        String modelCode,
        String displayName,
        ModelType modelType,
        Set<ModelCapability> capabilities,
        int contextWindow,
        Integer outputLimit,
        Integer embeddingDimension) {
}
