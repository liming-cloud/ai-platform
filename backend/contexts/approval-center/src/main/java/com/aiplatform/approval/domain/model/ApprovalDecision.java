package com.aiplatform.approval.domain.model;

import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;
import java.util.Objects;

/**
 * 审批决策值对象，表达审批人、动作、意见和决策时间。
 */
public record ApprovalDecision(
        String actorId,
        ApprovalDecisionAction action,
        String comment,
        Instant decidedAt
) implements ValueObject {
    public ApprovalDecision {
        actorId = requireText(actorId, "审批人 ID");
        Objects.requireNonNull(action, "审批动作不能为空");
        comment = requireText(comment, "审批原因");
        Objects.requireNonNull(decidedAt, "审批时间不能为空");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

}
