package com.aiplatform.approval.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.aiplatform.approval.support.ApprovalEvidenceTestFixtures.evidence;
import static com.aiplatform.approval.support.ApprovalEvidenceTestFixtures.targetDigest;

import com.aiplatform.approval.domain.model.ApprovalRequest;
import com.aiplatform.approval.domain.model.ApprovalRiskLevel;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ApprTaskPersistenceEntityTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-22T03:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void createsPendingApprovalTaskFromApprovalRequest() {
        ApprTaskPersistenceEntity entity = ApprTaskPersistenceEntity.fromRequest(new ApprovalRequest(
                TenantId.of("2001"),
                "REQ-3001",
                "model_config_change",
                "model-hub",
                "model_provider",
                "3001",
                "创建模型供应商 OpenAI 企业账号",
                ApprovalRiskLevel.HIGH_RISK,
                "1001",
                "TRACE-MDL-001",
                evidence("3001"),
                targetDigest("3001")
        ), FIXED_CLOCK);

        assertThat(entity.getTenantId()).isEqualTo(2001L);
        assertThat(entity.getApprovalRequestId()).isEqualTo("REQ-3001");
        assertThat(entity.getApprovalType()).isEqualTo("model_config_change");
        assertThat(entity.getSourceContext()).isEqualTo("model-hub");
        assertThat(entity.getSourceResourceType()).isEqualTo("model_provider");
        assertThat(entity.getSourceResourceId()).isEqualTo(3001L);
        assertThat(entity.getRiskLevel()).isEqualTo("HIGH_RISK");
        assertThat(entity.getRequesterId()).isEqualTo(1001L);
        assertThat(entity.getStatus()).isEqualTo("PENDING");
        assertThat(entity.getTraceId()).isEqualTo("TRACE-MDL-001");
        assertThat(entity.getCreatedAt()).isEqualTo(Instant.parse("2026-05-22T03:00:00Z"));
    }

    @Test
    void rejectsNonNumericRequesterIdWithChineseMessage() {
        assertThatThrownBy(() -> ApprTaskPersistenceEntity.fromRequest(new ApprovalRequest(
                        TenantId.of("2001"),
                        "REQ-3001",
                        "model_config_change",
                        "model-hub",
                        "model_provider",
                        "3001",
                        "创建模型供应商 OpenAI 企业账号",
                        ApprovalRiskLevel.HIGH_RISK,
                        "requester",
                        "TRACE-MDL-001",
                        evidence("3001"),
                        targetDigest("3001")
                ), FIXED_CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("申请人 ID 必须是数字型数据库主键");
    }
}
