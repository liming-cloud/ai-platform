package com.aiplatform.modelhub.infrastructure.messaging.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApprovalDecisionPayloadTest {
    @Test
    void approvedPayloadAllowsMissingComment() {
        ApprovalDecisionPayload payload = payload("APPROVED", "APPROVE", null);

        assertThat(payload.comment()).isEmpty();
        assertThat(payload.isApproved()).isTrue();
    }

    @Test
    void rejectsRejectedPayloadWithoutCommentInsteadOfEmptyFallback() {
        assertThatThrownBy(() -> payload("REJECTED", "REJECT", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("拒绝审批必须填写意见");
    }

    @Test
    void rejectsPayloadWithMismatchedStatusAndAction() {
        assertThatThrownBy(() -> payload("APPROVED", "REJECT", "缺少连通性证据"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批状态与审批动作不一致");
    }

    @Test
    void rejectsPayloadWithUnsupportedDecisionStatus() {
        assertThatThrownBy(() -> payload("PENDING", "APPROVE", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审批状态不支持");
    }

    @Test
    void normalizesPayloadFactsBeforeModelHubUse() {
        ApprovalDecisionPayload payload = payload(" REJECTED ", " REJECT ", " 缺少连通性证据 ");

        assertThat(payload.taskId()).isEqualTo("8101");
        assertThat(payload.approvalRequestId()).isEqualTo("REQ-8101");
        assertThat(payload.sourceContext()).isEqualTo("model-hub");
        assertThat(payload.sourceResourceType()).isEqualTo("model_provider");
        assertThat(payload.sourceResourceId()).isEqualTo("3001");
        assertThat(payload.status()).isEqualTo("REJECTED");
        assertThat(payload.action()).isEqualTo("REJECT");
        assertThat(payload.approverId()).isEqualTo("1001");
        assertThat(payload.comment()).isEqualTo("缺少连通性证据");
    }

    private static ApprovalDecisionPayload payload(String status, String action, String comment) {
        return new ApprovalDecisionPayload(
                " 8101 ",
                " REQ-8101 ",
                " model-hub ",
                " model_provider ",
                " 3001 ",
                status,
                action,
                " 1001 ",
                comment
        );
    }
}
