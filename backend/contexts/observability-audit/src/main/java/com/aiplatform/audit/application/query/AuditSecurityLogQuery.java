package com.aiplatform.audit.application.query;

import java.time.Instant;
import java.util.Objects;

/**
 * 已规范化且强制带租户范围的安全审计查询。
 */
public record AuditSecurityLogQuery(
        Long tenantId,
        Long actorId,
        String eventType,
        String subjectType,
        String subjectRef,
        String traceId,
        String riskLevel,
        String resultStatus,
        Instant createdFrom,
        Instant createdTo,
        int page,
        int pageSize
) {
    public AuditSecurityLogQuery {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页条数必须为 1 到 100");
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
    }

    public long offset() {
        return ((long) page - 1L) * pageSize;
    }

    /**
     * MyBatis 使用 JavaBean 属性解析访问计算后的偏移量。
     */
    public long getOffset() {
        return offset();
    }
}
