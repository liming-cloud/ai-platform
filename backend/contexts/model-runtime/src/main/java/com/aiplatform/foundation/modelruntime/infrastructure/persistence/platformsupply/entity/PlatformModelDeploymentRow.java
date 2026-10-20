package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity;

import java.time.Instant;

/** 平台部署专表的内部投影，数组转为确定文本，只在基础设施恢复领域值。 */
public record PlatformModelDeploymentRow(long id, long connectionId, long revision, String modelCode, String displayName,
        String modelType, String capabilities, int contextWindow, Integer outputLimit, Integer embeddingDimension,
        Long verificationId, Instant expiresAt, String status, long version, Instant createdAt,
        String parameterSource, String residency, Integer rerankCandidateLimit, String plannedTenantIds,
        Long discoveryId, String candidateDisplayName, String classification, Long subjectId, Instant declaredAt) { }
