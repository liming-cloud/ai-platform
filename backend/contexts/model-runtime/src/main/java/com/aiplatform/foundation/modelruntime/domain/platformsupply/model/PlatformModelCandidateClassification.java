package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

/** 平台模型候选的能力分类来源。 */
public enum PlatformModelCandidateClassification {
    CONFIRMED_BY_PROVIDER,
    CONFIRMED_BY_CONTROLLED_MAPPING,
    CONFIRMED_BY_ADMIN,
    REQUIRES_ADMIN_CONFIRMATION
}
