package com.aiplatform.approval.infrastructure.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.approval.domain.model.ApprovalRiskLevel;
import com.aiplatform.approval.domain.model.ApprovalStatus;
import com.aiplatform.approval.domain.model.ApprovalTask;
import com.aiplatform.approval.infrastructure.persistence.entity.ApprTaskPersistenceEntity;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApprovalTaskPersistenceConverterTest {
    private final ApprovalTaskPersistenceConverter converter = new ApprovalTaskPersistenceConverter();

    @Test
    void toDomainPreservesRequiredAuditFields() {
        ApprovalTask task = converter.toDomain(entity());

        assertThat(task.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(task.taskId()).isEqualTo("7001");
        assertThat(task.approvalRequestId()).isEqualTo("REQ-7001");
        assertThat(task.sourceResourceId()).isEqualTo("3001");
        assertThat(task.traceId()).isEqualTo("TRACE-APPROVAL-001");
        assertThat(task.version()).isEqualTo(3);
    }

    @Test
    void toDomainNormalizesPersistedTextFactsBeforeEnumAndAuditUse() {
        ApprTaskPersistenceEntity entity = entity();
        entity.setApprovalType(" model_config_change ");
        entity.setSourceContext(" model-hub ");
        entity.setSourceResourceType(" model_provider ");
        entity.setTitle(" 创建模型供应商 OpenAI ");
        entity.setRiskLevel(" HIGH_RISK ");
        entity.setStatus(" PENDING ");
        entity.setTraceId(" TRACE-APPROVAL-001 ");

        ApprovalTask task = converter.toDomain(entity);

        assertThat(task.approvalType()).isEqualTo("model_config_change");
        assertThat(task.sourceContext()).isEqualTo("model-hub");
        assertThat(task.sourceResourceType()).isEqualTo("model_provider");
        assertThat(task.title()).isEqualTo("创建模型供应商 OpenAI");
        assertThat(task.riskLevel()).isEqualTo(ApprovalRiskLevel.HIGH_RISK);
        assertThat(task.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(task.traceId()).isEqualTo("TRACE-APPROVAL-001");
    }

    @Test
    void toDomainRejectsMissingTraceIdInsteadOfEmptyFallback() {
        ApprTaskPersistenceEntity entity = entity();
        entity.setTraceId(null);

        assertThatThrownBy(() -> converter.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("调用链 ID不能为空");
    }

    @Test
    void toDomainRejectsMissingVersionInsteadOfZeroFallback() {
        ApprTaskPersistenceEntity entity = entity();
        entity.setVersion(null);

        assertThatThrownBy(() -> converter.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("乐观锁版本不能为空");
    }

    @Test
    void toDomainRejectsMissingTenantIdInsteadOfStringNull() {
        ApprTaskPersistenceEntity entity = entity();
        entity.setTenantId(null);

        assertThatThrownBy(() -> converter.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户 ID不能为空");
    }

    @Test
    void toDomainRejectsDecidedTaskWithoutApproverInsteadOfEmptyFallback() {
        ApprTaskPersistenceEntity entity = entity();
        entity.setStatus("APPROVED");
        entity.setDecisionAt(Instant.parse("2026-05-27T09:00:00Z"));
        entity.setApproverId(null);

        assertThatThrownBy(() -> converter.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审批人 ID不能为空");
    }

    @Test
    void approvalTaskRejectsNegativeVersion() {
        assertThatThrownBy(() -> new ApprovalTask(
                TenantId.of("2001"),
                "7001",
                "REQ-7001",
                "model_config_change",
                "model-hub",
                "model_provider",
                "3001",
                "创建模型供应商 OpenAI",
                ApprovalRiskLevel.HIGH_RISK,
                "1001",
                null,
                ApprovalStatus.PENDING,
                null,
                null,
                "TRACE-APPROVAL-001",
                Instant.parse("2026-05-27T08:00:00Z"),
                Instant.parse("2026-05-27T08:00:00Z"),
                -1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("乐观锁版本不能小于 0");
    }

    @Test
    void approvalTaskRejectsPendingTaskWithDecisionFacts() {
        assertThatThrownBy(() -> new ApprovalTask(
                TenantId.of("2001"),
                "7001",
                "REQ-7001",
                "model_config_change",
                "model-hub",
                "model_provider",
                "3001",
                "创建模型供应商 OpenAI",
                ApprovalRiskLevel.HIGH_RISK,
                "1001",
                "1002",
                ApprovalStatus.PENDING,
                null,
                null,
                "TRACE-APPROVAL-001",
                Instant.parse("2026-05-27T08:00:00Z"),
                Instant.parse("2026-05-27T08:00:00Z"),
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("待审批任务不能携带审批人 ID");
    }

    private static ApprTaskPersistenceEntity entity() {
        ApprTaskPersistenceEntity entity = new ApprTaskPersistenceEntity();
        entity.setId(7001L);
        entity.setTenantId(2001L);
        entity.setApprovalRequestId("REQ-7001");
        entity.setApprovalType("model_config_change");
        entity.setSourceContext("model-hub");
        entity.setSourceResourceType("model_provider");
        entity.setSourceResourceId(3001L);
        entity.setTitle("创建模型供应商 OpenAI");
        entity.setRiskLevel("HIGH_RISK");
        entity.setRequesterId(1001L);
        entity.setStatus("PENDING");
        entity.setTraceId("TRACE-APPROVAL-001");
        entity.setCreatedAt(Instant.parse("2026-05-27T08:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-05-27T08:00:00Z"));
        entity.setVersion(3);
        return entity;
    }
}
