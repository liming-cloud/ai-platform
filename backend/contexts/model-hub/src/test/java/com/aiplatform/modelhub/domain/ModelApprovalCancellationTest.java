package com.aiplatform.modelhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class ModelApprovalCancellationTest {
    @Test
    void canonicalizesRequestedAtToPostgresMicrosecondPrecision() {
        Instant sourceClock = Instant.parse("2026-07-21T08:00:00.123456789Z");

        ModelApprovalCancellation cancellation = cancellation("供应商地址录入错误", sourceClock);

        assertThat(cancellation.requestedAt()).isEqualTo(sourceClock.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void rejectsReasonsThatCannotCrossTheDurableProtocolBoundary() {
        assertThatThrownBy(() -> cancellation("a".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批撤销理由不能超过 500 个字符");
        assertThatThrownBy(() -> cancellation("地址错误\n伪造审计行"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批撤销理由不能包含控制字符");
    }

    private static ModelApprovalCancellation cancellation(String reason) {
        return cancellation(reason, Instant.parse("2026-07-21T08:00:00Z"));
    }

    private static ModelApprovalCancellation cancellation(String reason, Instant requestedAt) {
        return new ModelApprovalCancellation(
                TenantId.of("2001"),
                "REQ-3001",
                ModelConfigChange.RESOURCE_TYPE_MODEL_PROVIDER,
                "3001",
                "1002",
                reason,
                "TRACE-CANCEL-001",
                requestedAt
        );
    }
}
