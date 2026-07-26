package com.aiplatform.foundation.evaluationoperations.api.model;

import java.time.Instant;

/**
 * 当前 UTC 自然日查询配额的只读视图。
 */
public record DailyQueryQuotaView(
        long used,
        int limit,
        Instant resetsAt) {
}
