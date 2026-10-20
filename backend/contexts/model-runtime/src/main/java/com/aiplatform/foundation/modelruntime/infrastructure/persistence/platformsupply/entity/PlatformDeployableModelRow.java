package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity;

/** 当前可建立草稿的安全候选读取投影，不包含任何秘密或端点。 */
public record PlatformDeployableModelRow(String connectionId, long connectionRevision, String connectionDisplayName, String residency,
        String declaredCapability, String modelCode, String displayName, String modelType, String capabilities, String classification) { }
