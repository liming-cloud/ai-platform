package com.aiplatform.appstudio.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 创建聊天会话命令，仅允许为当前租户与指定应用创建新会话。
 */
public record CreateChatSessionCommand(
        TenantId tenantId,
        String actorId,
        String applicationId,
        String title,
        String traceId
) {
    public CreateChatSessionCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = requireText(actorId, "操作者 ID");
        applicationId = requireText(applicationId, "应用 ID");
        title = normalizeOptionalText(title);
        traceId = normalizeOptionalText(traceId);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null ? "" : value.trim();
    }
}
