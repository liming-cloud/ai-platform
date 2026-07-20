package com.aiplatform.approval.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import org.junit.jupiter.api.Test;

class DecideApprovalTaskCommandTest {
    private static final String EVIDENCE_DIGEST = "a".repeat(64);

    @Test
    void approveCommandRequiresReason() {
        assertThatThrownBy(() -> new DecideApprovalTaskCommand(
                TenantId.of("2001"),
                "1001",
                "8101",
                "APPROVE",
                null,
                EVIDENCE_DIGEST
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批原因不能为空");
    }

    @Test
    void rejectsRejectCommandWithoutCommentInsteadOfEmptyFallback() {
        assertThatThrownBy(() -> new DecideApprovalTaskCommand(
                TenantId.of("2001"),
                "1001",
                "8101",
                "REJECT",
                null,
                EVIDENCE_DIGEST
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批原因不能为空");
    }

    @Test
    void normalizesCommandFactsBeforeApplicationUse() {
        DecideApprovalTaskCommand command = new DecideApprovalTaskCommand(
                TenantId.of("2001"),
                " 1001 ",
                " 8101 ",
                " REJECT ",
                " 缺少连通性证据 ",
                " " + EVIDENCE_DIGEST.toUpperCase(java.util.Locale.ROOT) + " "
        );

        assertThat(command.actorId()).isEqualTo("1001");
        assertThat(command.taskId()).isEqualTo("8101");
        assertThat(command.action()).isEqualTo("REJECT");
        assertThat(command.comment()).isEqualTo("缺少连通性证据");
        assertThat(command.expectedEvidenceDigest()).isEqualTo(EVIDENCE_DIGEST);
    }

    @Test
    void rejectsMalformedExpectedEvidenceDigest() {
        assertThatThrownBy(() -> new DecideApprovalTaskCommand(
                TenantId.of("2001"),
                "1001",
                "8101",
                "APPROVE",
                "同意上线",
                "not-a-digest"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64 位 SHA-256");
    }
}
