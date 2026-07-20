package com.aiplatform.audit.infrastructure.persistence.mapper;

import com.aiplatform.audit.infrastructure.persistence.entity.AuditSecurityLogPersistenceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditSecurityLogMapper {
    int insertLog(AuditSecurityLogPersistenceEntity entity);
}
