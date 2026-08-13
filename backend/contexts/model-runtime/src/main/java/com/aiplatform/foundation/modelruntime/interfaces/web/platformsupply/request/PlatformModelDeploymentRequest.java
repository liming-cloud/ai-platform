package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import java.util.Set;

/** 创建平台模型部署的 HTTP 请求，不含端点或密钥。 */
public record PlatformModelDeploymentRequest(
        String providerConnectionId, long expectedConnectionVersion, String modelCode, String displayName,
        ModelType modelType, Set<ModelCapability> capabilities, int contextWindow,
        Integer outputLimit, Integer embeddingDimension) {
}
