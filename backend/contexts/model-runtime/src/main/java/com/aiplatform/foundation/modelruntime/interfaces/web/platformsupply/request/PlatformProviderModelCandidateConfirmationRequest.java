package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;

/** 管理员确认平台模型候选类型的 HTTP 请求。 */
public record PlatformProviderModelCandidateConfirmationRequest(
        long expectedConnectionVersion,
        String modelCode,
        ModelType modelType) {
}
