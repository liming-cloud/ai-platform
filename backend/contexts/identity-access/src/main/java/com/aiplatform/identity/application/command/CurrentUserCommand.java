package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 当前登录用户查询命令，必须携带服务端解析出的租户、令牌和链路追踪标识。
 */
public record CurrentUserCommand(
        TenantId tenantId,
        String tokenValue,
        String traceId
) {
    public CurrentUserCommand {
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
