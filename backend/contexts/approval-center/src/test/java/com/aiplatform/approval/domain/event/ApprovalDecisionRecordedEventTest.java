package com.aiplatform.approval.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApprovalDecisionRecordedEventTest {
    @Test
    void approvedEventAllowsMissingComment() {
        ApprovalDecisionRecordedEvent event = event("APPROVED", "APPROVE", null);

        assertThat(event.comment()).isEmpty();
    }

    @Test
    void rejectsRejectedEventWithoutCommentInsteadOfEmptyFallback() {
        assertThatThrownBy(() -> event("REJECTED", "REJECT", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("拒绝审批必须填写意见");
    }

    @Test
    void rejectsDecisionEventWithMismatchedStatusAndAction() {
        assertThatThrownBy(() -> event("APPROVED", "REJECT", "缺少连通性证据"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批状态与审批动作不一致");
    }

    @Test
    void rejectsDecisionEventWithUnsupportedStatus() {
        assertThatThrownBy(() -> event("CANCELLED", "APPROVE", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审批状态不支持");
    }

    @Test
    void rejectsDecisionEventWithoutTenantIdInsteadOfJdkNullPointerException() {
        assertThatThrownBy(() -> event(null, Instant.parse("2026-05-29T08:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
    }

    @Test
    void rejectsDecisionEventWithoutOccurredAtInsteadOfJdkNullPointerException() {
        assertThatThrownBy(() -> event(TenantId.of("2001"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("事件发生时间不能为空");
    }

    @Test
    void normalizesEventFactsBeforePublishing() {
        ApprovalDecisionRecordedEvent event = event(" REJECTED ", " REJECT ", " 缺少连通性证据 ");

        assertThat(event.taskId()).isEqualTo("8101");
        assertThat(event.approvalRequestId()).isEqualTo("REQ-8101");
        assertThat(event.sourceContext()).isEqualTo("model-hub");
        assertThat(event.sourceResourceType()).isEqualTo("model_provider");
        assertThat(event.sourceResourceId()).isEqualTo("3001");
        assertThat(event.status()).isEqualTo("REJECTED");
        assertThat(event.action()).isEqualTo("REJECT");
        assertThat(event.approverId()).isEqualTo("1001");
        assertThat(event.comment()).isEqualTo("缺少连通性证据");
        assertThat(event.traceId()).isEqualTo("TRACE-APPROVAL-001");
    }

    private static ApprovalDecisionRecordedEvent event(String status, String action, String comment) {
        return event(TenantId.of("2001"), status, action, comment, Instant.parse("2026-05-29T08:00:00Z"));
    }

    private static ApprovalDecisionRecordedEvent event(TenantId tenantId, Instant occurredAt) {
        return event(tenantId, "APPROVED", "APPROVE", null, occurredAt);
    }

    private static ApprovalDecisionRecordedEvent event(
            TenantId tenantId,
            String status,
            String action,
            String comment,
            Instant occurredAt
    ) {
        return new ApprovalDecisionRecordedEvent(
                tenantId,
                " 8101 ",
                " REQ-8101 ",
                " model-hub ",
                " model_provider ",
                " 3001 ",
                status,
                action,
                " 1001 ",
                comment,
                " TRACE-APPROVAL-001 ",
                " model-approval-evidence/v1 ",
                "a".repeat(64),
                "b".repeat(64),
                occurredAt
        );
    }
}
