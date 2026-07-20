package com.aiplatform.shared.audit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditEventTest {
    @Test
    void normalizesRequiredAuditFactsBeforePersistence() {
        AuditEvent event = new AuditEvent(
                " audit-001 ",
                " 2001 ",
                "1001",
                " model-provider ",
                "3001",
                " model-provider:create ",
                " medium ",
                null,
                " trace-001 ",
                Instant.parse("2026-06-02T10:00:00Z")
        );

        assertThat(event.eventId()).isEqualTo("audit-001");
        assertThat(event.tenantId()).isEqualTo("2001");
        assertThat(event.resourceType()).isEqualTo("model-provider");
        assertThat(event.action()).isEqualTo("model-provider:create");
        assertThat(event.riskLevel()).isEqualTo("medium");
        assertThat(event.traceId()).isEqualTo("trace-001");
    }

    @Test
    void keepsOptionalDetailAttributesImmutableAndSkipsNullValues() {
        AuditEvent event = new AuditEvent(
                "audit-002",
                "2001",
                "1001",
                "IAM_TENANT_FEATURE",
                null,
                "IAM_TENANT_FEATURE_UPDATE",
                "HIGH",
                null,
                "trace-002",
                Instant.parse("2026-06-02T10:00:00Z"),
                Map.of("featureCode", "identity-settings", "enabled", true)
        );

        assertThat(event.detailAttributes())
                .containsEntry("featureCode", "identity-settings")
                .containsEntry("enabled", true);
        assertThatThrownBy(() -> event.detailAttributes().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankRequiredAuditFacts() {
        Instant createdAt = Instant.parse("2026-06-02T10:00:00Z");

        assertThatThrownBy(() -> auditEvent(" ", "2001", "model-provider", "create", "medium", "trace", createdAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审计事件 ID不能为空");
        assertThatThrownBy(() -> auditEvent("audit-001", " ", "model-provider", "create", "medium", "trace", createdAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
        assertThatThrownBy(() -> auditEvent("audit-001", "2001", " ", "create", "medium", "trace", createdAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审计资源类型不能为空");
        assertThatThrownBy(() -> auditEvent("audit-001", "2001", "model-provider", " ", "medium", "trace", createdAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审计动作不能为空");
        assertThatThrownBy(() -> auditEvent("audit-001", "2001", "model-provider", "create", " ", "trace", createdAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审计风险等级不能为空");
        assertThatThrownBy(() -> auditEvent("audit-001", "2001", "model-provider", "create", "medium", " ", createdAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("调用链 ID不能为空");
        assertThatThrownBy(() -> auditEvent("audit-001", "2001", "model-provider", "create", "medium", "trace", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审计创建时间不能为空");
    }

    private static AuditEvent auditEvent(
            String eventId,
            String tenantId,
            String resourceType,
            String action,
            String riskLevel,
            String traceId,
            Instant createdAt
    ) {
        return new AuditEvent(eventId, tenantId, "1001", resourceType, "3001", action, riskLevel, null, traceId, createdAt);
    }
}
