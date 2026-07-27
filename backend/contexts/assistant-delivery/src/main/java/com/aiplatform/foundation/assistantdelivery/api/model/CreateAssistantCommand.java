package com.aiplatform.foundation.assistantdelivery.api.model;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 在可信租户内创建助手身份。
 */
public record CreateAssistantCommand(
        TrustedRequestContext context,
        String idempotencyKey,
        String displayName) {

    public CreateAssistantCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        requireKey(idempotencyKey);
        Objects.requireNonNull(displayName, "助手名称不能为空");
    }

    private static void requireKey(String value) {
        Objects.requireNonNull(value, "幂等键不能为空");
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 200) {
            throw new IllegalArgumentException("幂等键格式不正确");
        }
    }
}
