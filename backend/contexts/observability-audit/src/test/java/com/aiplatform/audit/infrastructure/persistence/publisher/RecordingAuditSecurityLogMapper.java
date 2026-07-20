package com.aiplatform.audit.infrastructure.persistence.publisher;

import com.aiplatform.audit.infrastructure.persistence.entity.AuditSecurityLogPersistenceEntity;
import com.aiplatform.audit.infrastructure.persistence.mapper.AuditSecurityLogMapper;

final class RecordingAuditSecurityLogMapper implements AuditSecurityLogMapper {
    private final int insertedRows;
    private AuditSecurityLogPersistenceEntity entity;

    RecordingAuditSecurityLogMapper() {
        this(1);
    }

    RecordingAuditSecurityLogMapper(int insertedRows) {
        this.insertedRows = insertedRows;
    }

    @Override
    public int insertLog(AuditSecurityLogPersistenceEntity entity) {
        this.entity = entity;
        return insertedRows;
    }

    AuditSecurityLogPersistenceEntity entity() {
        return entity;
    }
}
