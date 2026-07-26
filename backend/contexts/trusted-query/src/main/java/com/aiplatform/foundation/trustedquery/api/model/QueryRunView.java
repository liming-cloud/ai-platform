package com.aiplatform.foundation.trustedquery.api.model;

import com.aiplatform.foundation.modelruntime.api.model.ModelUsage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 查询运行的公共只读视图。
 */
public record QueryRunView(
        UUID id,
        UUID assistantVersionId,
        String status,
        String answer,
        String refusalReason,
        List<QueryCitationView> citations,
        ModelUsage usage,
        long version,
        Instant createdAt,
        Instant completedAt) {
}
