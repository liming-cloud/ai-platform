package com.aiplatform.foundation.modelruntime.application.provider.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.util.Objects;

/**
 * 在可信租户上下文中轮换一个厂商连接的 API Key。
 */
public record RotateProviderConnectionCredentialCommand(
        TrustedRequestContext context,
        String providerConnectionId,
        long expectedVersion,
        SecretValue apiKey) {

    public RotateProviderConnectionCredentialCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(providerConnectionId, "供应商连接标识不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("供应商连接版本不能为负数");
        }
        Objects.requireNonNull(apiKey, "API Key不能为空");
    }
}
