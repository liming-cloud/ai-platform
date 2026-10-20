package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity;

/** 仅用于候选分页的内部读取投影；合并明确管理员确认，不携带秘密、地址或原始响应。 */
public record PlatformProviderModelCandidateRow(String modelCode, String modelType, String capabilities, String classification, String displayName, long discoveryId) {
}
