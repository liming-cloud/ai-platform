package com.aiplatform.appstudio.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

public record RollbackApplicationReleaseCommand(
        TenantId tenantId,
        String actorId,
        String applicationId,
        String targetReleaseId,
        String rollbackRequestId,
        String reason
) {
    public RollbackApplicationReleaseCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = requireText(actorId, "操作者 ID");
        applicationId = requireText(applicationId, "应用 ID");
        targetReleaseId = requireText(targetReleaseId, "目标发布版本 ID");
        rollbackRequestId = requireText(rollbackRequestId, "回滚请求 ID");
        reason = requireText(reason, "回滚原因");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
