package com.aiplatform.approval.infrastructure.messaging.model;

/** model-hub 发出的审批撤销请求；任务缺席时 approval-center 仍需持久化该事实等待对账。 */
public record ModelApprovalCancellationPayload(
        String approvalRequestId,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String operatorId,
        String reason,
        String requestedAt
) {
}
