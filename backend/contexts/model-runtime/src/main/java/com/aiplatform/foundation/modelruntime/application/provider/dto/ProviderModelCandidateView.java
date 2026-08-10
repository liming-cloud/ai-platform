package com.aiplatform.foundation.modelruntime.application.provider.dto;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import java.util.Set;

/** 当前连接修订可创建草稿部署的受控候选摘要。 */
public record ProviderModelCandidateView(
        String modelCode,
        ModelType modelType,
        Set<ModelCapability> capabilities) {

    public ProviderModelCandidateView {
        capabilities = Set.copyOf(capabilities);
    }
}
