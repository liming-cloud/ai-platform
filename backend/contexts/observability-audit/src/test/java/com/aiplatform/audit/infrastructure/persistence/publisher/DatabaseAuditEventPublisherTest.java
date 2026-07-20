package com.aiplatform.audit.infrastructure.persistence.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.audit.infrastructure.persistence.entity.AuditOperationLogPersistenceEntity;
import com.aiplatform.audit.infrastructure.persistence.entity.AuditSecurityLogPersistenceEntity;
import com.aiplatform.audit.infrastructure.persistence.entity.RagAuthorizationDecisionLogPersistenceEntity;
import com.aiplatform.audit.infrastructure.persistence.mapper.RagAuthorizationDecisionLogMapper;
import com.aiplatform.shared.audit.model.AuditEvent;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatabaseAuditEventPublisherTest {
    private static final Instant CREATED_AT = Instant.parse("2026-05-29T10:15:30Z");

    @Test
    void ragSecurityEventWritesDecisionSummaryAndSecurityAudit() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper();
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper();
        RagAuthorizationDecisionLogMapper ragDecisions = mock(RagAuthorizationDecisionLogMapper.class);
        when(ragDecisions.insertLog(any())).thenReturn(1);
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                ragDecisions,
                new JsonMapper()
        );

        publisher.publish(new AuditEvent(
                "audit-rag-1001",
                "2001",
                "1003",
                "RAG_RETRIEVAL",
                "41,42",
                "SECURITY_RAG_CITATION_FILTERED",
                "HIGH",
                null,
                "trace-rag-filtered",
                CREATED_AT,
                Map.of("reason", "citation_acl_denied", "filteredCount", 2)
        ));

        ArgumentCaptor<RagAuthorizationDecisionLogPersistenceEntity> captor =
                ArgumentCaptor.forClass(RagAuthorizationDecisionLogPersistenceEntity.class);
        verify(ragDecisions).insertLog(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(2001L);
        assertThat(captor.getValue().getActorId()).isEqualTo(1003L);
        assertThat(captor.getValue().getDecision()).isEqualTo("FILTERED");
        assertThat(captor.getValue().getReasonCode()).isEqualTo("citation_acl_denied");
        assertThat(captor.getValue().getTraceId()).isEqualTo("trace-rag-filtered");
        assertThat(captor.getValue().getDetailsJson()).doesNotContain("prompt");
        assertThat(securityLogs.entity().getEventType()).isEqualTo("SECURITY_RAG_CITATION_FILTERED");
        assertThat(securityLogs.entity().getSubjectRef()).isEqualTo("41,42");
        assertThat(securityLogs.entity().getSubjectId()).isNull();
        assertThat(operationLogs.entity()).isNull();
    }

    @Test
    void loginFailureIsWrittenToSecurityAuditLog() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper();
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper();
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                new JsonMapper()
        );

        publisher.publish(new AuditEvent(
                "audit-1001",
                "2001",
                null,
                "IAM_USER",
                null,
                "LOGIN_FAILED",
                "MEDIUM",
                null,
                "trace-login-failed",
                CREATED_AT
        ));

        assertThat(operationLogs.entity()).isNull();
        AuditSecurityLogPersistenceEntity entity = securityLogs.entity();
        assertThat(entity.getTenantId()).isEqualTo(2001L);
        assertThat(entity.getActorId()).isNull();
        assertThat(entity.getEventType()).isEqualTo("LOGIN_FAILED");
        assertThat(entity.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(entity.getSubjectType()).isEqualTo("IAM_USER");
        assertThat(entity.getSubjectId()).isNull();
        assertThat(entity.getSubjectRef()).isNull();
        assertThat(entity.getResultStatus()).isEqualTo("FAILED");
        assertThat(entity.getDetailJson()).contains("audit-1001", "\"actorId\":null", "\"resourceId\":null");
        assertThat(entity.getTraceId()).isEqualTo("trace-login-failed");
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void securityAuditDetailJsonIncludesExtendedAttributesWithoutNumericSubjectId() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper();
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper();
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                new JsonMapper()
        );

        publisher.publish(new AuditEvent(
                "audit-1002",
                "2001",
                "1001",
                "IAM_TENANT_FEATURE",
                null,
                "IAM_TENANT_FEATURE_UPDATE",
                "HIGH",
                null,
                "trace-tenant-feature",
                CREATED_AT,
                Map.of("featureCode", "identity-settings", "enabled", true)
        ));

        assertThat(operationLogs.entity()).isNull();
        AuditSecurityLogPersistenceEntity entity = securityLogs.entity();
        assertThat(entity.getSubjectType()).isEqualTo("IAM_TENANT_FEATURE");
        assertThat(entity.getSubjectId()).isNull();
        assertThat(entity.getResultStatus()).isEqualTo("SUCCESS");
        assertThat(entity.getDetailJson())
                .contains("\"featureCode\":\"identity-settings\"")
                .contains("\"enabled\":true");
    }

    @Test
    void permissionRequestDecisionWithOpaqueSubjectReferenceIsWrittenToSecurityAudit() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper();
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper();
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                new JsonMapper()
        );
        String requestId = "90323c3e-569a-4f13-ab3a-a5a115edc2b5";

        publisher.publish(new AuditEvent(
                "audit-permission-request-decision",
                "2001",
                "1002",
                "IAM_PERMISSION_REQUEST",
                requestId,
                "PERMISSION_REQUEST_DECIDED",
                "HIGH",
                null,
                "trace-permission-request-decision",
                CREATED_AT,
                Map.of("decision", "APPROVED")
        ));

        assertThat(operationLogs.entity()).isNull();
        AuditSecurityLogPersistenceEntity entity = securityLogs.entity();
        assertThat(entity.getEventType()).isEqualTo("PERMISSION_REQUEST_DECIDED");
        assertThat(entity.getSubjectType()).isEqualTo("IAM_PERMISSION_REQUEST");
        assertThat(entity.getSubjectRef()).isEqualTo(requestId);
        assertThat(entity.getSubjectId()).isNull();
        assertThat(entity.getActorId()).isEqualTo(1002L);
    }

    @Test
    void numericSecuritySubjectKeepsLegacyProjection() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper();
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper();
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                new JsonMapper()
        );

        publisher.publish(new AuditEvent(
                "audit-numeric-security-subject",
                "2001",
                "1002",
                "IAM_ROLE",
                "3002",
                "IAM_ROLE_DEFAULT_MENU_UPDATE",
                "HIGH",
                null,
                "trace-numeric-security-subject",
                CREATED_AT
        ));

        AuditSecurityLogPersistenceEntity entity = securityLogs.entity();
        assertThat(entity.getSubjectRef()).isEqualTo("3002");
        assertThat(entity.getSubjectId()).isEqualTo(3002L);
        assertThat(operationLogs.entity()).isNull();
    }

    @Test
    void modelProviderCreationWithApprovalIsWrittenToOperationAuditLog() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper();
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper();
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                new JsonMapper()
        );

        publisher.publish(new AuditEvent(
                "audit-2001",
                "2001",
                "3001",
                "MODEL_PROVIDER",
                "7001",
                "MODEL_PROVIDER_CREATE",
                "HIGH",
                "approval-9001",
                "trace-model-provider",
                CREATED_AT
        ));

        assertThat(securityLogs.entity()).isNull();
        AuditOperationLogPersistenceEntity entity = operationLogs.entity();
        assertThat(entity.getTenantId()).isEqualTo(2001L);
        assertThat(entity.getActorId()).isEqualTo(3001L);
        assertThat(entity.getActionCode()).isEqualTo("MODEL_PROVIDER_CREATE");
        assertThat(entity.getResourceType()).isEqualTo("MODEL_PROVIDER");
        assertThat(entity.getResourceId()).isEqualTo(7001L);
        assertThat(entity.getResourceRef()).isEqualTo("7001");
        assertThat(entity.getResultStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(entity.getDetailJson()).contains("audit-2001", "approval-9001", "\"actorId\":\"3001\"");
        assertThat(entity.getTraceId()).isEqualTo("trace-model-provider");
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void opaqueResourceReferenceIsWrittenWithoutNumericProjection() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper();
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper();
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                new JsonMapper()
        );

        publisher.publish(new AuditEvent(
                "audit-opaque-resource",
                "2001",
                "3001",
                "WORKFLOW_EXECUTION",
                "90323c3e-569a-4f13-ab3a-a5a115edc2b5",
                "WORKFLOW_EXECUTION_RECORDED",
                "HIGH",
                null,
                "trace-opaque-resource",
                CREATED_AT
        ));

        assertThat(securityLogs.entity()).isNull();
        AuditOperationLogPersistenceEntity entity = operationLogs.entity();
        assertThat(entity.getResourceRef()).isEqualTo("90323c3e-569a-4f13-ab3a-a5a115edc2b5");
        assertThat(entity.getResourceId()).isNull();
    }

    @Test
    void missingResourceReferenceLeavesReferenceAndNumericProjectionEmpty() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper();
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper();
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                new JsonMapper()
        );

        publisher.publish(new AuditEvent(
                "audit-missing-resource",
                "2001",
                "3001",
                "WORKFLOW_EXECUTION",
                null,
                "WORKFLOW_EXECUTION_RECORDED",
                "LOW",
                null,
                "trace-missing-resource",
                CREATED_AT
        ));

        AuditOperationLogPersistenceEntity entity = operationLogs.entity();
        assertThat(entity.getResourceRef()).isNull();
        assertThat(entity.getResourceId()).isNull();
    }

    @Test
    void securityAuditFailsClosedWhenInsertAffectsNoRows() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper(0);
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper();
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                new JsonMapper()
        );

        assertThatThrownBy(() -> publisher.publish(new AuditEvent(
                "audit-2101",
                "2001",
                null,
                "IAM_USER",
                null,
                "LOGIN_FAILED",
                "MEDIUM",
                null,
                "trace-security-insert",
                CREATED_AT
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("安全审计日志写入失败，影响行数：0");
        assertThat(operationLogs.entity()).isNull();
    }

    @Test
    void operationAuditFailsClosedWhenInsertAffectsNoRows() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper();
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper(0);
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                new JsonMapper()
        );

        assertThatThrownBy(() -> publisher.publish(new AuditEvent(
                "audit-2102",
                "2001",
                "3001",
                "MODEL_PROVIDER",
                "7001",
                "MODEL_PROVIDER_CREATE",
                "HIGH",
                "approval-9001",
                "trace-operation-insert",
                CREATED_AT
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("操作审计日志写入失败，影响行数：0");
        assertThat(securityLogs.entity()).isNull();
    }

    @Test
    void rejectsOperationAuditWithNonNumericActorId() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper();
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper();
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                new JsonMapper()
        );

        assertThatThrownBy(() -> publisher.publish(new AuditEvent(
                "audit-3001",
                "2001",
                "operator-3001",
                "MODEL_PROVIDER",
                "7001",
                "MODEL_PROVIDER_CREATE",
                "HIGH",
                null,
                "trace-invalid-actor",
                CREATED_AT
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("操作人 ID必须是数字型数据库主键");
        assertThat(securityLogs.entity()).isNull();
        assertThat(operationLogs.entity()).isNull();
    }

    @Test
    void rejectsSecurityAuditWithNonNumericActorId() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper();
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper();
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                new JsonMapper()
        );

        assertThatThrownBy(() -> publisher.publish(new AuditEvent(
                "audit-3002",
                "2001",
                "operator-1002",
                "IAM_PERMISSION_REQUEST",
                "90323c3e-569a-4f13-ab3a-a5a115edc2b5",
                "PERMISSION_REQUEST_DECIDED",
                "HIGH",
                null,
                "trace-invalid-security-actor",
                CREATED_AT
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("操作人 ID必须是数字型数据库主键");
        assertThat(securityLogs.entity()).isNull();
        assertThat(operationLogs.entity()).isNull();
    }

    @Test
    void rejectsAuditWithNonNumericTenantId() {
        RecordingAuditSecurityLogMapper securityLogs = new RecordingAuditSecurityLogMapper();
        RecordingAuditOperationLogMapper operationLogs = new RecordingAuditOperationLogMapper();
        DatabaseAuditEventPublisher publisher = new DatabaseAuditEventPublisher(
                securityLogs,
                operationLogs,
                new JsonMapper()
        );

        assertThatThrownBy(() -> publisher.publish(new AuditEvent(
                "audit-invalid-tenant",
                "tenant-2001",
                "1002",
                "IAM_PERMISSION_REQUEST",
                "90323c3e-569a-4f13-ab3a-a5a115edc2b5",
                "PERMISSION_REQUEST_DECIDED",
                "HIGH",
                null,
                "trace-invalid-tenant",
                CREATED_AT
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户 ID必须是数字型数据库主键");
        assertThat(securityLogs.entity()).isNull();
        assertThat(operationLogs.entity()).isNull();
    }

    @Test
    void auditEventRejectsMissingRequiredAuditFacts() {
        assertThatThrownBy(() -> eventWithAction(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审计动作不能为空");
        assertThatThrownBy(() -> eventWithTraceId(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("调用链 ID不能为空");
        assertThatThrownBy(() -> new AuditEvent(
                "",
                "2001",
                "3001",
                "MODEL_PROVIDER",
                "7001",
                "MODEL_PROVIDER_CREATE",
                "HIGH",
                null,
                "trace-missing-event",
                CREATED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审计事件 ID不能为空");
    }

    private static AuditEvent eventWithAction(String action) {
        return new AuditEvent(
                "audit-missing-action",
                "2001",
                "3001",
                "MODEL_PROVIDER",
                "7001",
                action,
                "HIGH",
                null,
                "trace-missing-action",
                CREATED_AT
        );
    }

    private static AuditEvent eventWithTraceId(String traceId) {
        return new AuditEvent(
                "audit-missing-trace",
                "2001",
                "3001",
                "MODEL_PROVIDER",
                "7001",
                "MODEL_PROVIDER_CREATE",
                "HIGH",
                null,
                traceId,
                CREATED_AT
        );
    }
}
