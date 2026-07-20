package com.aiplatform.appstudio.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

public record RunPublishPreflightCommand(
        TenantId tenantId,
        String actorId,
        String applicationId,
        String publishRequestId
) {
    public RunPublishPreflightCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = requireText(actorId, "操作者 ID");
        applicationId = requireText(applicationId, "应用 ID");
        publishRequestId = requireText(publishRequestId, "发布请求 ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
