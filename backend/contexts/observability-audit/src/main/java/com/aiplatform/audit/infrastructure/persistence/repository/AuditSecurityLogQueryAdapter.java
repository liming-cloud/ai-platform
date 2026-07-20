package com.aiplatform.audit.infrastructure.persistence.repository;

import com.aiplatform.audit.application.port.AuditSecurityLogQueryPort;
import com.aiplatform.audit.application.query.AuditSecurityLogQuery;
import com.aiplatform.audit.application.result.AuditSecurityLogResult;
import com.aiplatform.audit.infrastructure.persistence.entity.AuditSecurityLogPersistenceEntity;
import com.aiplatform.audit.infrastructure.persistence.mapper.AuditSecurityLogQueryMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/**
 * 安全审计日志查询持久化适配器，负责把 MyBatis 实体转换为应用层结果。
 */
@Repository
public class AuditSecurityLogQueryAdapter implements AuditSecurityLogQueryPort {
    private final AuditSecurityLogQueryMapper mapper;

    public AuditSecurityLogQueryAdapter(AuditSecurityLogQueryMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "安全审计日志查询 Mapper 不能为空");
    }

    @Override
    public long countLogs(AuditSecurityLogQuery query) {
        return mapper.countLogs(Objects.requireNonNull(query, "安全审计日志查询不能为空"));
    }

    @Override
    public List<AuditSecurityLogResult> findLogs(AuditSecurityLogQuery query) {
        return mapper.findLogs(Objects.requireNonNull(query, "安全审计日志查询不能为空"))
                .stream()
                .map(AuditSecurityLogQueryAdapter::toResult)
                .toList();
    }

    private static AuditSecurityLogResult toResult(AuditSecurityLogPersistenceEntity entity) {
        Objects.requireNonNull(entity, "安全审计日志不能为空");
        String numericSubjectId = toStringOrNull(entity.getSubjectId());
        return new AuditSecurityLogResult(
                toStringOrNull(entity.getId()),
                toStringOrNull(entity.getTenantId()),
                toStringOrNull(entity.getActorId()),
                entity.getEventType(),
                entity.getRiskLevel(),
                entity.getSubjectType(),
                numericSubjectId,
                entity.getSubjectRef() == null ? numericSubjectId : entity.getSubjectRef(),
                entity.getResultStatus(),
                entity.getDetailJson(),
                entity.getTraceId(),
                entity.getCreatedAt()
        );
    }

    private static String toStringOrNull(Long value) {
        return value == null ? null : value.toString();
    }
}
