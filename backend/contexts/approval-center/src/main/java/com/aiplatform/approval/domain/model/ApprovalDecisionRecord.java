package com.aiplatform.approval.domain.model;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;
import java.util.Objects;

/**
 * 审批流水值对象，用于持久化审批动作审计记录。
 */
public record ApprovalDecisionRecord(
        TenantId tenantId,
        String taskId,
        String actorId,
        ApprovalDecisionAction action,
        String comment,
        Instant actionAt
) implements ValueObject {
    public ApprovalDecisionRecord {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        taskId = requireText(taskId, "审批任务 ID");
        actorId = requireText(actorId, "审批人 ID");
        Objects.requireNonNull(action, "审批动作不能为空");
        comment = normalizeHistoricalComment(action, comment);
        Objects.requireNonNull(actionAt, "审批动作时间不能为空");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeHistoricalComment(ApprovalDecisionAction action, String comment) {
        if (action == ApprovalDecisionAction.REJECT) {
            if (comment == null || comment.isBlank()) {
                throw new IllegalArgumentException("拒绝审批必须填写意见");
            }
            return comment.trim();
        }
        return comment == null ? "" : comment.trim();
    }
}
