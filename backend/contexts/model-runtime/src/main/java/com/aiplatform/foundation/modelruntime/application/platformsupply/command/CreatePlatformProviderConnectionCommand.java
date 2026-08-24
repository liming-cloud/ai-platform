package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

/** 在已验证的平台上下文中建立平台供应商连接。 */
public record CreatePlatformProviderConnectionCommand(
        PlatformRequestContext context,
        String idempotencyKey,
        ProviderKind providerKind,
        PlatformModelDiscoveryProfile discoveryProfile,
        String displayName,
        URI endpoint,
        String region,
        AuthenticationType authenticationType,
        SecretValue apiKey) {

    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");

    public CreatePlatformProviderConnectionCommand {
        Objects.requireNonNull(context, "平台请求上下文不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("幂等键格式不正确");
        }
        Objects.requireNonNull(providerKind, "供应商类型不能为空");
        Objects.requireNonNull(discoveryProfile, "模型发现协议不能为空");
        if (!discoveryProfile.supports(providerKind)) {
            throw new IllegalArgumentException("模型发现协议与供应商类型不兼容");
        }
        Objects.requireNonNull(endpoint, "供应商端点不能为空");
        Objects.requireNonNull(authenticationType, "认证方式不能为空");
        Objects.requireNonNull(apiKey, "API Key不能为空");
    }

    /** 仅供历史内部调用兼容；公共 HTTP 写入必须显式提交发现协议。 */
    public CreatePlatformProviderConnectionCommand(
            PlatformRequestContext context,
            String idempotencyKey,
            ProviderKind providerKind,
            String displayName,
            URI endpoint,
            String region,
            AuthenticationType authenticationType,
            SecretValue apiKey) {
        this(context, idempotencyKey, providerKind,
                PlatformModelDiscoveryProfile.legacyDefault(providerKind), displayName,
                endpoint, region, authenticationType, apiKey);
    }
}
