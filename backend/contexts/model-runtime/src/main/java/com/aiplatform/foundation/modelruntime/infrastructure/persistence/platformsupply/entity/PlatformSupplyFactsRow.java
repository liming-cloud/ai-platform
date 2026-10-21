package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.publication.PlatformSupplyFacts;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** 独立发布及有界授权集合的安全持久化投影，不返回连接地址或凭据。 */
public record PlatformSupplyFactsRow(String publicationId, long deploymentRevision, String catalogEntryId,
        Instant publishedAt, long selectedTenantCount, long activeGrantCount, long revokedGrantCount,
        long dependentConfigurationCount, String activeTenantIds, boolean currentSourceAvailable) {
    /** PostgreSQL 有序聚合转为公开字符串 ID；空集与未读取分别表达。 */
    public PlatformSupplyFacts view() {
        return new PlatformSupplyFacts(publicationId, deploymentRevision, catalogEntryId, publishedAt,
                selectedTenantCount, activeGrantCount, revokedGrantCount, dependentConfigurationCount,
                activeTenantIds.isEmpty() ? List.of() : Arrays.asList(activeTenantIds.split(",")), currentSourceAvailable);
    }
}
