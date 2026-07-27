package com.aiplatform.foundation.evaluationoperations.application.operations.dto;

/**
 * 当前有效运行租约的并发配额视图。
 */
public record ConcurrentQueryQuotaView(
        long used,
        int limit) {
}
