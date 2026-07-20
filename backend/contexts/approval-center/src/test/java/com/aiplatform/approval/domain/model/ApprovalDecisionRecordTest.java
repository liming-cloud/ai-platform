package com.aiplatform.approval.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApprovalDecisionRecordTest {
    @Test
    void approveRecordAllowsMissingComment() {
        ApprovalDecisionRecord record = new ApprovalDecisionRecord(
                TenantId.of("2001"),
                "8101",
                "1001",
                ApprovalDecisionAction.APPROVE,
                null,
                Instant.parse("2026-05-29T08:00:00Z")
        );

        assertThat(record.comment()).isEmpty();
    }

    @Test
    void rejectsRejectRecordWithoutCommentInsteadOfEmptyFallback() {
        assertThatThrownBy(() -> new ApprovalDecisionRecord(
                TenantId.of("2001"),
                "8101",
                "1001",
                ApprovalDecisionAction.REJECT,
                null,
                Instant.parse("2026-05-29T08:00:00Z")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("拒绝审批必须填写意见");
    }
}
