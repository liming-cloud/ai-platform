package com.aiplatform.audit.infrastructure.persistence.mapper;

import com.aiplatform.audit.application.query.AuditSecurityLogQuery;
import com.aiplatform.audit.infrastructure.persistence.entity.AuditSecurityLogPersistenceEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 安全审计只读 Mapper，写入路径由 {@link AuditSecurityLogMapper} 独立负责。
 */
@Mapper
public interface AuditSecurityLogQueryMapper {
    long countLogs(@Param("query") AuditSecurityLogQuery query);

    List<AuditSecurityLogPersistenceEntity> findLogs(@Param("query") AuditSecurityLogQuery query);
}
