package com.aiplatform.audit.infrastructure.persistence.mapper;

import com.aiplatform.audit.infrastructure.persistence.entity.AuditOperationLogPersistenceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditOperationLogMapper {
    int insertLog(AuditOperationLogPersistenceEntity entity);
}
