package com.aiplatform.approval.domain.event;

import com.aiplatform.approval.domain.model.ApprovalDecision;
import com.aiplatform.approval.domain.model.ApprovalDecisionAction;
import com.aiplatform.approval.domain.model.ApprovalStatus;
import com.aiplatform.approval.domain.model.ApprovalTask;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;

/**
 * 审批决策领域事件，供来源上下文消费并完成本地状态流转。
 */
public record ApprovalDecisionRecordedEvent(
        TenantId tenantId,
        String taskId,
        String approvalRequestId,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String status,
        String action,
        String approverId,
        String comment,
        String traceId,
        String evidenceSchema,
        String evidenceDigest,
        String targetDigest,
        Instant occurredAt
) implements ValueObject {
    public ApprovalDecisionRecordedEvent {
        tenantId = requireValue(tenantId, "租户 ID");
        taskId = requireText(taskId, "审批任务 ID");
        approvalRequestId = requireText(approvalRequestId, "审批请求 ID");
        sourceContext = requireText(sourceContext, "来源上下文");
        sourceResourceType = requireText(sourceResourceType, "来源资源类型");
        sourceResourceId = requireText(sourceResourceId, "来源资源 ID");
        status = requireText(status, "审批状态");
        action = requireText(action, "审批动作");
        validateDecisionStatus(status, action);
        approverId = requireText(approverId, "审批人 ID");
        comment = normalizeComment(action, comment);
        traceId = requireText(traceId, "调用链 ID");
        evidenceSchema = requireText(evidenceSchema, "审批证据 schema");
        evidenceDigest = requireDigest(evidenceDigest, "审批证据摘要");
        targetDigest = requireDigest(targetDigest, "审批目标摘要");
        occurredAt = requireValue(occurredAt, "事件发生时间");
    }

    public static ApprovalDecisionRecordedEvent from(ApprovalTask task, ApprovalDecision decision) {
        ApprovalTask decidedTask = task.decide(decision);
        return new ApprovalDecisionRecordedEvent(
                task.tenantId(),
                task.taskId(),
                task.approvalRequestId(),
                task.sourceContext(),
                task.sourceResourceType(),
                task.sourceResourceId(),
                decidedTask.status().name(),
                decision.action().name(),
                decision.actorId(),
                decision.comment(),
                task.traceId(),
                task.evidence().schema(),
                task.evidence().digest(),
                task.targetDigest(),
                decision.decidedAt()
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static String requireDigest(String value, String fieldName) {
        String normalized = requireText(value, fieldName).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(fieldName + "必须是 64 位 SHA-256 十六进制值");
        }
        return normalized;
    }

    private static void validateDecisionStatus(String status, String action) {
        ApprovalStatus decisionStatus = approvalStatus(status);
        ApprovalDecisionAction decisionAction = approvalAction(action);
        if (decisionAction.targetStatus() != decisionStatus) {
            throw new IllegalArgumentException("审批状态与审批动作不一致");
        }
    }

    private static ApprovalStatus approvalStatus(String value) {
        if (!ApprovalStatus.APPROVED.name().equals(value)
                && !ApprovalStatus.REJECTED.name().equals(value)) {
            throw new IllegalArgumentException("审批状态不支持：" + value);
        }
        return ApprovalStatus.valueOf(value);
    }

    private static ApprovalDecisionAction approvalAction(String value) {
        try {
            return ApprovalDecisionAction.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("审批动作不支持：" + value, exception);
        }
    }

    private static String normalizeComment(String action, String comment) {
        if (ApprovalDecisionAction.REJECT.name().equalsIgnoreCase(action)) {
            if (comment == null || comment.isBlank()) {
                throw new IllegalArgumentException("拒绝审批必须填写意见");
            }
            return comment.trim();
        }
        return comment == null ? "" : comment.trim();
    }
}
