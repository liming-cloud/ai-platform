package com.aiplatform.audit.presentation.response;

import com.aiplatform.audit.application.result.AuditSecurityLogResult;
import java.time.Instant;
import java.util.Objects;

public record AuditSecurityLogResponse(
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
    public static AuditSecurityLogResponse from(AuditSecurityLogResult result, String disclosedDetailJson) {
        Objects.requireNonNull(result, "安全审计日志不能为空");
        return new AuditSecurityLogResponse(
                result.id(),
                result.tenantId(),
                result.actorId(),
                result.eventType(),
                result.riskLevel(),
                result.subjectType(),
                result.subjectId(),
                result.subjectRef(),
                result.resultStatus(),
                Objects.requireNonNull(disclosedDetailJson, "安全审计披露详情不能为空"),
                result.traceId(),
                result.createdAt()
        );
    }
}
