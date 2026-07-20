package com.aiplatform.audit.infrastructure.persistence.publisher;

import com.aiplatform.audit.infrastructure.persistence.entity.AuditOperationLogPersistenceEntity;
import com.aiplatform.audit.infrastructure.persistence.mapper.AuditOperationLogMapper;

final class RecordingAuditOperationLogMapper implements AuditOperationLogMapper {
    private final int insertedRows;
    private AuditOperationLogPersistenceEntity entity;

    RecordingAuditOperationLogMapper() {
        this(1);
    }

    RecordingAuditOperationLogMapper(int insertedRows) {
        this.insertedRows = insertedRows;
    }

    @Override
    public int insertLog(AuditOperationLogPersistenceEntity entity) {
        this.entity = entity;
        return insertedRows;
    }

    AuditOperationLogPersistenceEntity entity() {
        return entity;
    }
}
