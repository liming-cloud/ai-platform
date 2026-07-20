package com.aiplatform.approval.application.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.approval.domain.model.ApprovalRiskLevel;
import com.aiplatform.approval.domain.model.ApprovalStatus;
import com.aiplatform.approval.domain.model.ApprovalTask;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApprovalTaskResultTest {
    @Test
    void taskResultNormalizesRequiredApprovalFacts() {
        ApprovalTaskResult result = new ApprovalTaskResult(
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

        assertThat(result.taskId()).isEqualTo("7001");
        assertThat(result.approvalRequestId()).isEqualTo("REQ-7001");
        assertThat(result.approvalType()).isEqualTo("model_config_change");
        assertThat(result.sourceContext()).isEqualTo("model-hub");
        assertThat(result.sourceResourceType()).isEqualTo("model_provider");
        assertThat(result.sourceResourceId()).isEqualTo("3001");
        assertThat(result.title()).isEqualTo("创建模型供应商 OpenAI");
        assertThat(result.riskLevel()).isEqualTo("HIGH_RISK");
        assertThat(result.requesterId()).isEqualTo("1001");
        assertThat(result.approverId()).isEqualTo("9001");
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.traceId()).isEqualTo("TRACE-APPROVAL-001");
    }

    @Test
    void pendingTaskKeepsMissingApproverAsNullInsteadOfEmptyFallback() {
        ApprovalTaskResult result = ApprovalTaskResult.from(task(null, ApprovalStatus.PENDING, null));

        assertThat(result.approverId()).isNull();
    }

    @Test
    void decidedTaskRejectsMissingApproverInsteadOfEmptyFallback() {
        assertThatThrownBy(() -> new ApprovalTaskResult(
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
                "APPROVED",
                "TRACE-APPROVAL-001"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审批人 ID不能为空");
    }

    @Test
    void rejectsBlankRequiredTaskFacts() {
        assertThatThrownBy(() -> new ApprovalTaskResult(
                " ",
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
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审批任务 ID不能为空");
    }

    private static ApprovalTask task(String approverId, ApprovalStatus status, Instant decisionAt) {
        return new ApprovalTask(
                TenantId.of("2001"),
                "7001",
                "REQ-7001",
                "model_config_change",
                "model-hub",
                "model_provider",
                "3001",
                "创建模型供应商 OpenAI",
                ApprovalRiskLevel.HIGH_RISK,
                "1001",
                approverId,
                status,
                null,
                decisionAt,
                "TRACE-APPROVAL-001",
                Instant.parse("2026-05-27T08:00:00Z"),
                Instant.parse("2026-05-27T08:00:00Z"),
                0
        );
    }
}
