package com.aiplatform.approval.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 审批任务决策命令，租户和操作者必须来自服务端上下文。
 */
public record DecideApprovalTaskCommand(
        TenantId tenantId,
        String actorId,
        String taskId,
        String action,
        String comment,
        String expectedEvidenceDigest
) {
    public DecideApprovalTaskCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = requireText(actorId, "审批人 ID");
        taskId = requireText(taskId, "审批任务 ID");
        action = requireText(action, "审批动作");
        comment = requireText(comment, "审批原因");
        expectedEvidenceDigest = requireDigest(expectedEvidenceDigest, "预期审批证据摘要");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String requireDigest(String value, String fieldName) {
        String normalized = requireText(value, fieldName).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(fieldName + "必须是 64 位 SHA-256 十六进制值");
        }
        return normalized;
    }

}
