package com.aiplatform.appstudio.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;
import java.util.UUID;

/**
 * 发布 AI 应用命令。
 */
public record PublishAiApplicationCommand(
        TenantId tenantId,
        String actorId,
        String applicationId,
        String publishRequestId,
        String reason
) {
    public PublishAiApplicationCommand(TenantId tenantId, String actorId, String applicationId) {
        this(tenantId, actorId, applicationId, "legacy-" + UUID.randomUUID(), null);
    }

    public PublishAiApplicationCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = requireText(actorId, "操作者 ID");
        applicationId = requireText(applicationId, "应用 ID");
        publishRequestId = requireText(publishRequestId, "发布请求 ID");
        reason = normalizeOptionalText(reason);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
