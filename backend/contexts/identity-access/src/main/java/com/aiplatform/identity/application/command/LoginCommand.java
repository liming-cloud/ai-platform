package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 用户登录命令，所有入口适配器必须先解析租户再调用登录用例。
 */
public record LoginCommand(
        TenantId tenantId,
        String loginName,
        String rawPassword,
        String traceId
) {
    public LoginCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        loginName = requireText(loginName, "登录名");
        rawPassword = requireRawPassword(rawPassword);
        traceId = requireText(traceId, "链路追踪 ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String requireRawPassword(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("登录密码不能为空");
        }
        return value;
    }
}
