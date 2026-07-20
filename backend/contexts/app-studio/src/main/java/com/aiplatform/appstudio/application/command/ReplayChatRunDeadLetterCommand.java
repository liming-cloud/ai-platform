package com.aiplatform.appstudio.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/** Administrator-authored, tenant-scoped and idempotent chat finalization replay command. */
public record ReplayChatRunDeadLetterCommand(
        TenantId tenantId,
        String runId,
        String actorId,
        String replayRequestId,
        String reason,
        String traceId
) {
    public ReplayChatRunDeadLetterCommand {
        tenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        runId = requireText(runId, "运行 ID", 64);
        actorId = requireText(actorId, "操作者 ID", 64);
        replayRequestId = requireText(replayRequestId, "重放请求 ID", 128);
        reason = requireText(reason, "重放原因", 512);
        traceId = requireText(traceId, "调用链 ID", 64);
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength);
        }
        return normalized;
    }
}
