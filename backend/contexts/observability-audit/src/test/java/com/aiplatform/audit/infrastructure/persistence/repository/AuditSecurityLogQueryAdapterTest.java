package com.aiplatform.audit.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.audit.application.query.AuditSecurityLogQuery;
import com.aiplatform.audit.application.result.AuditSecurityLogResult;
import com.aiplatform.audit.infrastructure.persistence.entity.AuditSecurityLogPersistenceEntity;
import com.aiplatform.audit.infrastructure.persistence.mapper.AuditSecurityLogQueryMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditSecurityLogQueryAdapterTest {
    private static final String OPAQUE_SUBJECT_REF = "90323c3e-569a-4f13-ab3a-a5a115edc2b5";

    @Test
    void exposesOpaqueReferenceAndBackfillsCompatibilityReferenceFromNumericRows() {
        AuditSecurityLogQueryMapper mapper = mock(AuditSecurityLogQueryMapper.class);
        AuditSecurityLogQuery query = new AuditSecurityLogQuery(
                2001L, null, null, null, OPAQUE_SUBJECT_REF,
                null, null, null, null, null, 1, 20
        );
        AuditSecurityLogPersistenceEntity opaque = entity(null, OPAQUE_SUBJECT_REF);
        AuditSecurityLogPersistenceEntity legacyNumeric = entity(3002L, null);
        when(mapper.findLogs(query)).thenReturn(List.of(opaque, legacyNumeric));

        List<AuditSecurityLogResult> results = new AuditSecurityLogQueryAdapter(mapper).findLogs(query);

        assertThat(results.get(0).subjectId()).isNull();
        assertThat(results.get(0).subjectRef()).isEqualTo(OPAQUE_SUBJECT_REF);
        assertThat(results.get(1).subjectId()).isEqualTo("3002");
        assertThat(results.get(1).subjectRef()).isEqualTo("3002");
    }

    private static AuditSecurityLogPersistenceEntity entity(Long subjectId, String subjectRef) {
        AuditSecurityLogPersistenceEntity entity = new AuditSecurityLogPersistenceEntity();
        entity.setId(subjectId == null ? 118L : 119L);
        entity.setTenantId(2001L);
        entity.setActorId(1002L);
        entity.setEventType("PERMISSION_REQUEST_DECIDED");
        entity.setRiskLevel("HIGH");
        entity.setSubjectType("IAM_PERMISSION_REQUEST");
        entity.setSubjectId(subjectId);
        entity.setSubjectRef(subjectRef);
        entity.setResultStatus("SUCCESS");
        entity.setDetailJson("{}");
        entity.setTraceId("trace-audit-query");
        entity.setCreatedAt(Instant.parse("2026-07-22T08:00:00Z"));
        return entity;
    }
}
