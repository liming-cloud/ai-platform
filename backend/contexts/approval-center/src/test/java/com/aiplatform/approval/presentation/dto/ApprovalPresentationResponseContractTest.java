package com.aiplatform.approval.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApprovalPresentationResponseContractTest {
    @Test
    void taskResponseNormalizesRequiredApprovalFacts() {
        ApprovalTaskResponse response = new ApprovalTaskResponse(
                " 7001 ",
                " REQ-7001 ",
                " model_config_change ",
                " model-hub ",
                " model_provider ",
                " 3001 ",
                " 创建模型供应商 OpenAI ",
                " HIGH_RISK ",
                " 1001 ",
                " 9001 ",
                " APPROVED ",
                " TRACE-APPROVAL-001 "
        );

        assertThat(response.taskId()).isEqualTo("7001");
        assertThat(response.approvalRequestId()).isEqualTo("REQ-7001");
        assertThat(response.approvalType()).isEqualTo("model_config_change");
        assertThat(response.sourceContext()).isEqualTo("model-hub");
        assertThat(response.sourceResourceType()).isEqualTo("model_provider");
        assertThat(response.sourceResourceId()).isEqualTo("3001");
        assertThat(response.title()).isEqualTo("创建模型供应商 OpenAI");
        assertThat(response.riskLevel()).isEqualTo("HIGH_RISK");
        assertThat(response.riskLevelLabel()).isEqualTo("高风险");
        assertThat(response.requesterId()).isEqualTo("1001");
        assertThat(response.approverId()).isEqualTo("9001");
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.statusLabel()).isEqualTo("已通过");
        assertThat(response.sourceContextLabel()).isEqualTo("模型中心");
        assertThat(response.traceId()).isEqualTo("TRACE-APPROVAL-001");
    }

    @Test
    void normalRiskUsesTheCanonicalEnterpriseLabel() {
        ApprovalTaskResponse response = new ApprovalTaskResponse(
                "7002",
                "REQ-7002",
                "role_change",
                "identity-access",
                "role",
                "3002",
                "调整业务角色权限",
                "NORMAL",
                "1001",
                null,
                "PENDING",
                "TRACE-APPROVAL-002"
        );

        assertThat(response.riskLevelLabel()).isEqualTo("常规");
        assertThat(response.sourceContextLabel()).isEqualTo("身份与权限");
    }

    @Test
    void pendingTaskResponseKeepsMissingApproverAsNull() {
        ApprovalTaskResponse response = new ApprovalTaskResponse(
                "7001",
                "REQ-7001",
                "model_config_change",
                "model-hub",
                "model_provider",
                "3001",
                "创建模型供应商 OpenAI",
                "HIGH_RISK",
                "1001",
                null,
                "PENDING",
                "TRACE-APPROVAL-001"
        );

        assertThat(response.approverId()).isNull();
    }

    @Test
    void cancelledTaskUsesTruthfulEnterpriseLabel() {
        assertThat(ApprovalTaskResponse.labelStatus("CANCELLED")).isEqualTo("已撤销");
    }

    @Test
    void decisionResponseNormalizesRequiredApprovalFacts() {
        ApprovalDecisionResponse response = new ApprovalDecisionResponse(
                " 7001 ",
                " REQ-7001 ",
                " REJECTED ",
                " model-hub ",
                " model_provider ",
                " 3001 ",
                " model-approval-evidence/v1 ",
                " " + "a".repeat(64) + " ",
                " " + "b".repeat(64) + " "
        );

        assertThat(response.taskId()).isEqualTo("7001");
        assertThat(response.approvalRequestId()).isEqualTo("REQ-7001");
        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.sourceContext()).isEqualTo("model-hub");
        assertThat(response.sourceResourceType()).isEqualTo("model_provider");
        assertThat(response.sourceResourceId()).isEqualTo("3001");
        assertThat(response.evidenceSchema()).isEqualTo("model-approval-evidence/v1");
        assertThat(response.evidenceDigest()).isEqualTo("a".repeat(64));
        assertThat(response.targetDigest()).isEqualTo("b".repeat(64));
    }

    @Test
    void responseRejectsBlankRequiredFacts() {
        assertThatThrownBy(() -> new ApprovalTaskResponse(
                "7001",
                "REQ-7001",
                "model_config_change",
                "model-hub",
                "model_provider",
                "3001",
                " ",
                "HIGH_RISK",
                "1001",
                null,
                "PENDING",
                "TRACE-APPROVAL-001"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审批标题不能为空");

        assertThatThrownBy(() -> new ApprovalDecisionResponse(
                "7001",
                "REQ-7001",
                "APPROVED",
                " ",
                "model_provider",
                "3001",
                "model-approval-evidence/v1",
                "a".repeat(64),
                "b".repeat(64)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("来源上下文不能为空");
    }

    @Test
    void decidedTaskResponseRejectsMissingApprover() {
        assertThatThrownBy(() -> new ApprovalTaskResponse(
                "7001",
                "REQ-7001",
                "model_config_change",
                "model-hub",
                "model_provider",
                "3001",
                "创建模型供应商 OpenAI",
                "HIGH_RISK",
                "1001",
                null,
                "REJECTED",
                "TRACE-APPROVAL-001"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审批人 ID不能为空");
    }
}
