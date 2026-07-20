package com.aiplatform.approval.presentation.dto;

/**
 * 审批决策响应 DTO，返回任务状态和来源资源定位。
 */
public record ApprovalDecisionResponse(
        String taskId,
        String approvalRequestId,
        String status,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String evidenceSchema,
        String evidenceDigest,
        String targetDigest
) {
    public ApprovalDecisionResponse {
        taskId = ApprovalResponseContracts.requireText(taskId, "审批任务 ID");
        approvalRequestId = ApprovalResponseContracts.requireText(approvalRequestId, "审批请求 ID");
        status = ApprovalResponseContracts.requireText(status, "审批状态");
        sourceContext = ApprovalResponseContracts.requireText(sourceContext, "来源上下文");
        sourceResourceType = ApprovalResponseContracts.requireText(sourceResourceType, "来源资源类型");
        sourceResourceId = ApprovalResponseContracts.requireText(sourceResourceId, "来源资源 ID");
        evidenceSchema = ApprovalResponseContracts.requireText(evidenceSchema, "审批证据 schema");
        evidenceDigest = ApprovalResponseContracts.requireText(evidenceDigest, "审批证据摘要");
        targetDigest = ApprovalResponseContracts.requireText(targetDigest, "审批目标摘要");
    }
}
