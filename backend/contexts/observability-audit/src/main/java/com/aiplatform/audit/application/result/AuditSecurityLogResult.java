package com.aiplatform.audit.application.result;

import java.time.Instant;

/**
 * 安全审计日志查询结果，隔离应用层和持久化实体。
 */
public record AuditSecurityLogResult(
        String id,
        String tenantId,
        String actorId,
        String eventType,
        String riskLevel,
        String subjectType,
        String subjectId,
        String subjectRef,
        String resultStatus,
        String detailJson,
        String traceId,
        Instant createdAt
) {
}
