package com.aiplatform.audit.application;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.audit.application.port.AuditSecurityLogQueryPort;
import com.aiplatform.audit.application.query.AuditSecurityLogQuery;
import com.aiplatform.audit.application.result.AuditSecurityLogPageResult;
import com.aiplatform.audit.application.result.AuditSecurityLogResult;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 安全审计日志查询服务，只允许按当前租户读取审计事实。
 */
@Service
public class AuditSecurityLogQueryService {
    private final AuditSecurityLogQueryPort queryPort;

    public AuditSecurityLogQueryService(AuditSecurityLogQueryPort queryPort) {
        this.queryPort = Objects.requireNonNull(queryPort, "安全审计日志查询端口不能为空");
    }

    @Transactional(readOnly = true)
    public AuditSecurityLogPageResult findLogs(
            TenantId tenantId,
            String actorId,
            String eventType,
            String subjectType,
            String subjectId,
            String traceId,
            String riskLevel,
            String resultStatus,
            String createdFrom,
            String createdTo,
            int page,
            int pageSize
    ) {
        Instant normalizedCreatedFrom = parseOptionalInstant(createdFrom, "开始时间");
        Instant normalizedCreatedTo = parseOptionalInstant(createdTo, "结束时间");
        AuditSecurityLogQuery query = new AuditSecurityLogQuery(
                parseTenantId(tenantId),
                parseOptionalLong(actorId, "操作人 ID"),
                normalizeCategory(eventType),
                normalizeCategory(subjectType),
                normalize(subjectId),
                normalize(traceId),
                normalizeCategory(riskLevel),
                normalizeCategory(resultStatus),
                normalizedCreatedFrom,
                normalizedCreatedTo,
                page,
                pageSize
        );
        long total = queryPort.countLogs(query);
        List<AuditSecurityLogResult> items = total == 0
                ? List.of()
                : queryPort.findLogs(query);
        return new AuditSecurityLogPageResult(items, query.page(), query.pageSize(), total);
    }

    private static Long parseTenantId(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        return parseRequiredLong(tenantId.value(), "租户 ID");
    }

    private static Long parseOptionalLong(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return parseRequiredLong(normalized, fieldName);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeCategory(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static Instant parseOptionalInstant(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(fieldName + "必须为 ISO-8601 时间", exception);
        }
    }
}
