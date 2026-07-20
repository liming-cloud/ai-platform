package com.aiplatform.approval.application.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApprovalDecisionResultTest {
    @Test
    void decisionResultNormalizesRequiredApprovalFacts() {
        ApprovalDecisionResult result = new ApprovalDecisionResult(
                " 7001 ",
                " REQ-7001 ",
                " APPROVED ",
                " model-hub ",
                " model_provider ",
                " 3001 ",
                " model-approval-evidence/v1 ",
                " " + "a".repeat(64) + " ",
                " " + "b".repeat(64) + " "
        );

        assertThat(result.taskId()).isEqualTo("7001");
        assertThat(result.approvalRequestId()).isEqualTo("REQ-7001");
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.sourceContext()).isEqualTo("model-hub");
        assertThat(result.sourceResourceType()).isEqualTo("model_provider");
        assertThat(result.sourceResourceId()).isEqualTo("3001");
        assertThat(result.evidenceSchema()).isEqualTo("model-approval-evidence/v1");
        assertThat(result.evidenceDigest()).isEqualTo("a".repeat(64));
        assertThat(result.targetDigest()).isEqualTo("b".repeat(64));
    }

    @Test
    void decisionResultRejectsBlankRequiredFacts() {
        assertThatThrownBy(() -> new ApprovalDecisionResult(
                "7001",
                "REQ-7001",
                " ",
                "model-hub",
                "model_provider",
                "3001",
                "model-approval-evidence/v1",
                "a".repeat(64),
                "b".repeat(64)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审批状态不能为空");
    }
}
