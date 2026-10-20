package com.aiplatform.foundation.modelruntime.domain.deployment.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
/**
 * 平台可据此验证和路由的模型能力，不根据厂商名称推断能力。
 */
public enum ModelCapability implements ValueObject {
    CHAT,
    STREAMING_CHAT,
    EMBEDDING,
    STRUCTURED_OUTPUT,
    TOOL_CALLING,
    RERANK
}
