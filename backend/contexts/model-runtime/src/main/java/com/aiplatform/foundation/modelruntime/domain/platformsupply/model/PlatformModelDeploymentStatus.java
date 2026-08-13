package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

/** 平台模型部署的不可逆生命周期状态。 */
public enum PlatformModelDeploymentStatus {
    DRAFT,
    VERIFIED,
    PUBLISHED,
    DISABLED
}
