package com.aiplatform.appstudio.application.command;

import com.aiplatform.appstudio.domain.debug.DebugEvidenceView;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

public record GetDebugEvidenceCommand(
        TenantId tenantId,
        String actorId,
        String applicationId,
        String assistantMessageId,
        DebugEvidenceView view,
        String reason
) {
    public GetDebugEvidenceCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = requireText(actorId, "操作者 ID");
        applicationId = requireText(applicationId, "应用 ID");
        assistantMessageId = requireText(assistantMessageId, "助手消息 ID");
        Objects.requireNonNull(view, "调试证据视图不能为空");
        reason = normalizeOptional(reason);
        if (view == DebugEvidenceView.FULL && reason == null) {
            throw new IllegalArgumentException("查看完整调试证据必须填写 reason");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
