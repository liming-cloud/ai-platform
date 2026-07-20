package com.aiplatform.approval.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.aiplatform.approval.support.ApprovalEvidenceTestFixtures.evidence;
import static com.aiplatform.approval.support.ApprovalEvidenceTestFixtures.targetDigest;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApprovalDomainInvariantsTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-05-29T08:00:00Z");

    @Test
    void normalizesApprovalRequestAndPendingTaskFactsBeforePersistenceUse() {
        ApprovalRequest request = new ApprovalRequest(
                TENANT_ID,
                " REQ-8101 ",
                " model-config ",
                " model-hub ",
                " model_provider ",
                " 3001 ",
                " 模型供应商审批 ",
                ApprovalRiskLevel.HIGH_RISK,
                " 1001 ",
                " TRACE-APPROVAL-001 ",
                evidence("3001"),
                targetDigest("3001")
        );
        ApprovalTask task = new ApprovalTask(
                TENANT_ID,
                " 8101 ",
                " REQ-8101 ",
                " model-config ",
                " model-hub ",
                " model_provider ",
                " 3001 ",
                " 模型供应商审批 ",
                ApprovalRiskLevel.HIGH_RISK,
                " 1001 ",
                null,
                ApprovalStatus.PENDING,
                null,
                null,
                " TRACE-APPROVAL-001 ",
                NOW,
                NOW,
                0
        );

        assertThat(request.approvalType()).isEqualTo("model-config");
        assertThat(request.approvalRequestId()).isEqualTo("REQ-8101");
        assertThat(request.sourceContext()).isEqualTo("model-hub");
        assertThat(request.sourceResourceType()).isEqualTo("model_provider");
        assertThat(request.sourceResourceId()).isEqualTo("3001");
        assertThat(request.title()).isEqualTo("模型供应商审批");
        assertThat(request.requesterId()).isEqualTo("1001");
        assertThat(request.traceId()).isEqualTo("TRACE-APPROVAL-001");
        assertThat(request.evidence().digest()).isEqualTo(evidence("3001").digest());
        assertThat(task.taskId()).isEqualTo("8101");
        assertThat(task.approvalRequestId()).isEqualTo("REQ-8101");
        assertThat(task.approvalType()).isEqualTo("model-config");
        assertThat(task.sourceContext()).isEqualTo("model-hub");
        assertThat(task.sourceResourceType()).isEqualTo("model_provider");
        assertThat(task.sourceResourceId()).isEqualTo("3001");
        assertThat(task.title()).isEqualTo("模型供应商审批");
        assertThat(task.requesterId()).isEqualTo("1001");
        assertThat(task.traceId()).isEqualTo("TRACE-APPROVAL-001");
    }

    @Test
    void normalizesApprovalDecisionAndRecordFactsBeforeAuditUse() {
        ApprovalDecision decision = new ApprovalDecision(
                " 1002 ",
                ApprovalDecisionAction.REJECT,
                " 缺少连通性证据 ",
                NOW
        );
        ApprovalDecisionRecord record = new ApprovalDecisionRecord(
                TENANT_ID,
                " 8101 ",
                " 1002 ",
                ApprovalDecisionAction.REJECT,
                " 缺少连通性证据 ",
                NOW
        );

        assertThat(decision.actorId()).isEqualTo("1002");
        assertThat(decision.comment()).isEqualTo("缺少连通性证据");
        assertThat(record.taskId()).isEqualTo("8101");
        assertThat(record.actorId()).isEqualTo("1002");
        assertThat(record.comment()).isEqualTo("缺少连通性证据");
    }

    @Test
    void normalizesDecidedTaskApproverFactsBeforeAuditUse() {
        ApprovalTask task = new ApprovalTask(
                TENANT_ID,
                " 8101 ",
                " REQ-8101 ",
                " model-config ",
                " model-hub ",
                " model_provider ",
                " 3001 ",
                " 模型供应商审批 ",
                ApprovalRiskLevel.HIGH_RISK,
                " 1001 ",
                " 1002 ",
                ApprovalStatus.APPROVED,
                null,
                NOW,
                " TRACE-APPROVAL-001 ",
                NOW,
                NOW,
                1
        );

        assertThat(task.approverId()).isEqualTo("1002");
    }

    @Test
    void rejectsMissingApprovalTaskCoreFactsInsteadOfJdkNullPointerException() {
        assertThatThrownBy(() -> taskWith(null, ApprovalRiskLevel.HIGH_RISK, ApprovalStatus.PENDING, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
        assertThatThrownBy(() -> taskWith(TENANT_ID, null, ApprovalStatus.PENDING, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("风险等级不能为空");
        assertThatThrownBy(() -> taskWith(TENANT_ID, ApprovalRiskLevel.HIGH_RISK, null, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批状态不能为空");
        assertThatThrownBy(() -> taskWith(TENANT_ID, ApprovalRiskLevel.HIGH_RISK, ApprovalStatus.PENDING, null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("创建时间不能为空");
        assertThatThrownBy(() -> taskWith(TENANT_ID, ApprovalRiskLevel.HIGH_RISK, ApprovalStatus.PENDING, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("更新时间不能为空");
    }

    @Test
    void rejectsMissingApprovalDecisionFactsInsteadOfJdkNullPointerException() {
        ApprovalTask pendingTask = taskWith(
                TENANT_ID,
                ApprovalRiskLevel.HIGH_RISK,
                ApprovalStatus.PENDING,
                NOW,
                NOW
        );
        assertThatThrownBy(() -> pendingTask.decide(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批决策不能为空");
        assertThatThrownBy(() -> new ApprovalTask(
                TENANT_ID,
                "8101",
                "REQ-8101",
                "model-config",
                "model-hub",
                "model_provider",
                "3001",
                "模型供应商审批",
                ApprovalRiskLevel.HIGH_RISK,
                "1001",
                "1002",
                ApprovalStatus.APPROVED,
                null,
                null,
                "TRACE-APPROVAL-001",
                NOW,
                NOW,
                1
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批决策时间不能为空");
    }

    @Test
    void cancellationReasonRejectsOversizedOrControlCharacterEvidence() {
        assertThatThrownBy(() -> cancellation("a".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批撤销理由不能超过 500 个字符");
        assertThatThrownBy(() -> cancellation("伪造理由\n下一行"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批撤销理由不能包含控制字符");
    }

    private static ApprovalCancellationRequest cancellation(String reason) {
        return new ApprovalCancellationRequest(
                TENANT_ID,
                "ModelApprovalCancellationRequested:2001:model_provider:REQ-8101",
                "REQ-8101",
                "model-hub",
                "model_provider",
                "3001",
                "1002",
                reason,
                "TRACE-CANCEL-001",
                NOW
        );
    }

    private static ApprovalTask taskWith(
            TenantId tenantId,
            ApprovalRiskLevel riskLevel,
            ApprovalStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new ApprovalTask(
                tenantId,
                "8101",
                "REQ-8101",
                "model-config",
                "model-hub",
                "model_provider",
                "3001",
                "模型供应商审批",
                riskLevel,
                "1001",
                null,
                status,
                null,
                null,
                "TRACE-APPROVAL-001",
                createdAt,
                updatedAt,
                0
        );
    }
}
