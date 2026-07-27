package com.aiplatform.foundation.modelruntime.interfaces.web.deployment.request;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import java.util.Set;

/**
 * 创建模型部署的 HTTP 请求。
 */
public record ModelDeploymentRequest(
        String providerConnectionId,
        String modelCode,
        String displayName,
        ModelType modelType,
        Set<ModelCapability> capabilities,
        int contextWindow,
        Integer outputLimit,
        Integer embeddingDimension) {
}
