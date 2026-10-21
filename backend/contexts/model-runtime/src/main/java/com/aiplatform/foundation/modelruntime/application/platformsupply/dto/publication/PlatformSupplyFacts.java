package com.aiplatform.foundation.modelruntime.application.platformsupply.dto.publication;

import java.time.Instant;

/** 平台独立发布及授权计数；活动授权不等于租户、来源或配置当前可用。 */
public record PlatformSupplyFacts(String publicationId, long deploymentRevision, String catalogEntryId,
        Instant publishedAt, long selectedTenantCount, long activeGrantCount, long revokedGrantCount,
        long dependentConfigurationCount, java.util.List<String> activeTenantIds, boolean currentSourceAvailable) {
    public PlatformSupplyFacts { activeTenantIds = java.util.List.copyOf(activeTenantIds); }
}
