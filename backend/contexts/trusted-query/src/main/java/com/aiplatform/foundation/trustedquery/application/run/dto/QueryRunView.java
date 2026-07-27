package com.aiplatform.foundation.trustedquery.application.run.dto;

import com.aiplatform.foundation.trustedquery.domain.query.model.QueryModelUsage;
import java.time.Instant;
import java.util.List;

/**
 * 查询运行的公共只读视图。
 */
public record QueryRunView(
        String id,
        String assistantVersionId,
        String status,
        String answer,
        String refusalReason,
        List<QueryCitationView> citations,
        QueryModelUsage usage,
        long version,
        Instant createdAt,
        Instant completedAt) {
}
