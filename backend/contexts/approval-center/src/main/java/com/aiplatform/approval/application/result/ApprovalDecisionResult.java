package com.aiplatform.approval.application.result;

import com.aiplatform.approval.domain.model.ApprovalTask;

/**
 * 审批决策结果，返回任务最终状态和来源资源定位信息。
 */
public record ApprovalDecisionResult(
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
    public ApprovalDecisionResult {
        taskId = ApprovalResultContracts.requireText(taskId, "审批任务 ID");
        approvalRequestId = ApprovalResultContracts.requireText(approvalRequestId, "审批请求 ID");
        status = ApprovalResultContracts.requireText(status, "审批状态");
        sourceContext = ApprovalResultContracts.requireText(sourceContext, "来源上下文");
        sourceResourceType = ApprovalResultContracts.requireText(sourceResourceType, "来源资源类型");
        sourceResourceId = ApprovalResultContracts.requireText(sourceResourceId, "来源资源 ID");
        evidenceSchema = ApprovalResultContracts.requireText(evidenceSchema, "审批证据 schema");
        evidenceDigest = ApprovalResultContracts.requireText(evidenceDigest, "审批证据摘要");
        targetDigest = ApprovalResultContracts.requireText(targetDigest, "审批目标摘要");
    }

    public static ApprovalDecisionResult from(ApprovalTask task) {
        return new ApprovalDecisionResult(
                task.taskId(),
                task.approvalRequestId(),
                task.status().name(),
                task.sourceContext(),
                task.sourceResourceType(),
                task.sourceResourceId(),
                task.evidence().schema(),
                task.evidence().digest(),
                task.targetDigest()
        );
    }
}
