package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 用户登出命令，表达需要失效的访问令牌和审计链路。
 */
public record LogoutCommand(
        TenantId tenantId,
        String tokenValue,
        String traceId
) {
    public LogoutCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        tokenValue = requireText(tokenValue, "访问令牌");
        traceId = requireText(traceId, "链路追踪 ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
