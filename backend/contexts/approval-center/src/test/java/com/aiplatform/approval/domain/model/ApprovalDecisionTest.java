package com.aiplatform.approval.domain.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApprovalDecisionTest {
    @Test
    void approveDecisionRequiresReason() {
        assertThatThrownBy(() -> new ApprovalDecision(
                "1001",
                ApprovalDecisionAction.APPROVE,
                null,
                Instant.parse("2026-05-29T08:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批原因不能为空");
    }

    @Test
    void rejectsRejectDecisionWithoutCommentInsteadOfEmptyFallback() {
        assertThatThrownBy(() -> new ApprovalDecision(
                "1001",
                ApprovalDecisionAction.REJECT,
                null,
                Instant.parse("2026-05-29T08:00:00Z")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批原因不能为空");
    }
}
