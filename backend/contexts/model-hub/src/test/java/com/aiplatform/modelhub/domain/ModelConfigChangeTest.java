package com.aiplatform.modelhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelConfigChangeTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-05-22T04:00:00Z");

    @Test
    void rejectsBlankRequiredChangeFactsInsteadOfInvalidApprovalChange() {
        assertBlankRejected("配置变更ID", " ", "9001", "model_provider_change", "model_provider", "9001", "创建模型供应商 OpenAI", "1001", "TRACE-001");
        assertBlankRejected("模型供应商ID", "CHANGE-001", " ", "model_provider_change", "model_provider", "9001", "创建模型供应商 OpenAI", "1001", "TRACE-001");
        assertBlankRejected("审批类型", "CHANGE-001", "9001", " ", "model_provider", "9001", "创建模型供应商 OpenAI", "1001", "TRACE-001");
        assertBlankRejected("来源资源类型", "CHANGE-001", "9001", "model_provider_change", " ", "9001", "创建模型供应商 OpenAI", "1001", "TRACE-001");
        assertBlankRejected("来源资源ID", "CHANGE-001", "9001", "model_provider_change", "model_provider", " ", "创建模型供应商 OpenAI", "1001", "TRACE-001");
        assertBlankRejected("配置变更摘要", "CHANGE-001", "9001", "model_provider_change", "model_provider", "9001", " ", "1001", "TRACE-001");
        assertBlankRejected("操作人ID", "CHANGE-001", "9001", "model_provider_change", "model_provider", "9001", "创建模型供应商 OpenAI", " ", "TRACE-001");
        assertBlankRejected("链路追踪ID", "CHANGE-001", "9001", "model_provider_change", "model_provider", "9001", "创建模型供应商 OpenAI", "1001", " ");
    }

    @Test
    void trimsRequiredChangeFactsBeforeAuditAndApprovalUsage() {
        ModelConfigChange change = configChange(
                " CHANGE-001 ",
                " 9001 ",
                " model_config_change ",
                " model_config ",
                " 4001 ",
                " 创建模型配置 GPT 4.1 Mini ",
                " 1001 ",
                " TRACE-001 "
        );

        assertThat(change.changeId()).isEqualTo("CHANGE-001");
        assertThat(change.providerId()).isEqualTo("9001");
        assertThat(change.approvalType()).isEqualTo("model_config_change");
        assertThat(change.sourceResourceType()).isEqualTo("model_config");
        assertThat(change.sourceResourceId()).isEqualTo("4001");
        assertThat(change.changeSummary()).isEqualTo("创建模型配置 GPT 4.1 Mini");
        assertThat(change.operatorId()).isEqualTo("1001");
        assertThat(change.traceId()).isEqualTo("TRACE-001");
        assertThat(change.auditResourceType()).isEqualTo("MODEL_CONFIG");
        assertThat(change.auditAction()).isEqualTo("MODEL_CONFIG_CREATE");
    }

    private static void assertBlankRejected(
            String fieldName,
            String changeId,
            String providerId,
            String approvalType,
            String sourceResourceType,
            String sourceResourceId,
            String changeSummary,
            String operatorId,
            String traceId
    ) {
        assertThatThrownBy(() -> configChange(
                changeId,
                providerId,
                approvalType,
                sourceResourceType,
                sourceResourceId,
                changeSummary,
                operatorId,
                traceId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(fieldName + "不能为空");
    }

    private static ModelConfigChange configChange(
            String changeId,
            String providerId,
            String approvalType,
            String sourceResourceType,
            String sourceResourceId,
            String changeSummary,
            String operatorId,
            String traceId
    ) {
        String normalizedChangeId = changeId.trim();
        String normalizedProviderId = providerId.trim();
        String normalizedApprovalType = approvalType.trim();
        String normalizedResourceType = sourceResourceType.trim();
        String normalizedResourceId = sourceResourceId.trim();
        String normalizedOperatorId = operatorId.trim();
        String modelConfigId = "model_config".equals(normalizedResourceType) ? normalizedResourceId : null;
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("tenantId", TENANT_ID.value());
        facts.put("approvalRequestId", normalizedChangeId);
        facts.put("approvalType", normalizedApprovalType);
        facts.put("sourceContext", "model-hub");
        facts.put("resourceType", normalizedResourceType);
        facts.put("resourceId", normalizedResourceId);
        facts.put("providerId", normalizedProviderId);
        facts.put("modelConfigId", modelConfigId);
        facts.put("requesterId", normalizedOperatorId);
        facts.put("requestedAt", NOW.toString());
        facts.put("riskLevel", ModelConfigRiskLevel.HIGH_RISK.name());
        facts.put("target", Map.of("resourceId", normalizedResourceId));
        ApprovalEvidenceEnvelope evidence = ApprovalEvidenceEnvelope.create(facts);
        String targetDigest = ApprovalEvidenceEnvelope.digestFacts(
                Map.of("target", Map.of("resourceId", normalizedResourceId))
        );
        return new ModelConfigChange(
                TENANT_ID,
                changeId,
                providerId,
                modelConfigId,
                approvalType,
                sourceResourceType,
                sourceResourceId,
                changeSummary,
                ModelConfigRiskLevel.HIGH_RISK,
                operatorId,
                traceId,
                NOW,
                evidence,
                targetDigest
        );
    }
}
