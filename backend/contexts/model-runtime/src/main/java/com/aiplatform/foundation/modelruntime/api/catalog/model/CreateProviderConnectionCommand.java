package com.aiplatform.foundation.modelruntime.api.catalog.model;

import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.model.AuthenticationType;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 在可信租户上下文中创建供应商连接。
 */
public record CreateProviderConnectionCommand(
        TrustedRequestContext context,
        String idempotencyKey,
        ProviderKind providerKind,
        String displayName,
        URI endpoint,
        String region,
        AuthenticationType authenticationType,
        String credentialReference) {

    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");

    public CreateProviderConnectionCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("幂等键格式不正确");
        }
        Objects.requireNonNull(providerKind, "供应商类型不能为空");
        Objects.requireNonNull(endpoint, "供应商端点不能为空");
        Objects.requireNonNull(authenticationType, "认证方式不能为空");
        Objects.requireNonNull(credentialReference, "密钥引用不能为空");
    }
}
