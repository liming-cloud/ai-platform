package com.aiplatform.modelhub.infrastructure.messaging.model;

/**
 * v1 审批决策载荷，仅用于滚动升级期间恢复已在 broker 中的旧消息。
 */
public record LegacyApprovalDecisionPayload(
        String taskId,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String status,
        String action,
        String approverId,
        String comment
) {
    private static final String VALIDATION_SENTINEL = "legacy-request-id-pending-resolution";

    public LegacyApprovalDecisionPayload {
        ApprovalDecisionPayload validated = new ApprovalDecisionPayload(
                taskId,
                VALIDATION_SENTINEL,
                sourceContext,
                sourceResourceType,
                sourceResourceId,
                status,
                action,
                approverId,
                comment
        );
        taskId = validated.taskId();
        sourceContext = validated.sourceContext();
        sourceResourceType = validated.sourceResourceType();
        sourceResourceId = validated.sourceResourceId();
        status = validated.status();
        action = validated.action();
        approverId = validated.approverId();
        comment = validated.comment();
    }

    public ApprovalDecisionPayload withApprovalRequestId(String approvalRequestId) {
        return new ApprovalDecisionPayload(
                taskId,
                approvalRequestId,
                sourceContext,
                sourceResourceType,
                sourceResourceId,
                status,
                action,
                approverId,
                comment
        );
    }
}
