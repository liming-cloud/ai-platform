package com.aiplatform.foundation.modelruntime.domain.deployment.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
/**
 * 模型部署生命周期状态。
 */
public enum ModelDeploymentStatus implements ValueObject {
    DRAFT,
    VERIFIED,
    PUBLISHED,
    DISABLED
}
