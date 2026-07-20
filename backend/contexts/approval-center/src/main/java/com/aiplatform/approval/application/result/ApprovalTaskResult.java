package com.aiplatform.approval.application.result;

import com.aiplatform.approval.domain.model.ApprovalTask;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批任务结果，供控制台展示审批任务和来源资源状态。
 */
public record ApprovalTaskResult(
        String taskId,
        String approvalRequestId,
        String approvalType,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String title,
        String riskLevel,
        String requesterId,
        String approverId,
        String status,
        String traceId,
        String evidenceSchema,
        String evidenceDigest,
        String targetDigest,
        Map<String, Object> evidence
) {
    /** Read-only representation of a pre-evidence task. */
    public ApprovalTaskResult(
            String taskId,
            String approvalRequestId,
            String approvalType,
            String sourceContext,
            String sourceResourceType,
            String sourceResourceId,
            String title,
            String riskLevel,
            String requesterId,
            String approverId,
            String status,
            String traceId
    ) {
        this(
                taskId,
                approvalRequestId,
                approvalType,
                sourceContext,
                sourceResourceType,
                sourceResourceId,
                title,
                riskLevel,
                requesterId,
                approverId,
                status,
                traceId,
                null,
                null,
                null,
                null
        );
    }

    public ApprovalTaskResult {
        taskId = ApprovalResultContracts.requireText(taskId, "审批任务 ID");
        approvalRequestId = ApprovalResultContracts.requireText(approvalRequestId, "审批请求 ID");
        approvalType = ApprovalResultContracts.requireText(approvalType, "审批类型");
        sourceContext = ApprovalResultContracts.requireText(sourceContext, "来源上下文");
        sourceResourceType = ApprovalResultContracts.requireText(sourceResourceType, "来源资源类型");
        sourceResourceId = ApprovalResultContracts.requireText(sourceResourceId, "来源资源 ID");
        title = ApprovalResultContracts.requireText(title, "审批标题");
        riskLevel = ApprovalResultContracts.requireText(riskLevel, "风险等级");
        requesterId = ApprovalResultContracts.requireText(requesterId, "申请人 ID");
        status = ApprovalResultContracts.requireText(status, "审批状态");
        approverId = ApprovalResultContracts.normalizeApproverId(status, approverId);
        traceId = ApprovalResultContracts.requireText(traceId, "调用链 ID");
        if (evidence == null) {
            if (evidenceSchema != null || evidenceDigest != null || targetDigest != null) {
                throw new IllegalArgumentException("审批证据元数据必须同时存在");
            }
        } else {
            evidenceSchema = ApprovalResultContracts.requireText(evidenceSchema, "审批证据 schema");
            evidenceDigest = ApprovalResultContracts.requireText(evidenceDigest, "审批证据摘要");
            targetDigest = ApprovalResultContracts.requireText(targetDigest, "审批目标摘要");
            // Creation evidence intentionally contains null before/modelConfigId values.
            evidence = Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
        }
    }

    public static ApprovalTaskResult from(ApprovalTask task) {
        return new ApprovalTaskResult(
                task.taskId(),
                task.approvalRequestId(),
                task.approvalType(),
                task.sourceContext(),
                task.sourceResourceType(),
                task.sourceResourceId(),
                task.title(),
                task.riskLevel().name(),
                task.requesterId(),
                task.approverId(),
                task.status().name(),
                task.traceId(),
                task.evidence() == null ? null : task.evidence().schema(),
                task.evidence() == null ? null : task.evidence().digest(),
                task.targetDigest(),
                task.evidence() == null ? null : task.evidence().facts()
        );
    }

}
