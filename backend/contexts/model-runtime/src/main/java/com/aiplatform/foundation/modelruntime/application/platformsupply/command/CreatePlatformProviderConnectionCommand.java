package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection;
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
        PlatformProviderPresetSelection preset,
        String displayName,
        URI endpoint,
        String region,
        AuthenticationType authenticationType,
        SecretValue apiKey,
        SecretValue discoveryAccessKeyId,
        SecretValue discoverySecretAccessKey) {

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
        Objects.requireNonNull(preset, "厂商预设来源不能为空");
        Objects.requireNonNull(displayName, "连接名称不能为空");
        if (!discoveryProfile.supports(providerKind)) {
            throw new IllegalArgumentException("模型发现协议与供应商类型不兼容");
        }
        Objects.requireNonNull(endpoint, "供应商端点不能为空");
        Objects.requireNonNull(region, "供应商区域不能为空");
        Objects.requireNonNull(authenticationType, "认证方式不能为空");
        Objects.requireNonNull(apiKey, "API Key不能为空");
        var arkDiscovery = discoveryProfile == PlatformModelDiscoveryProfile.VOLCENGINE_ARK_CONTROL_PLANE;
        if (arkDiscovery ? discoveryAccessKeyId == null || discoverySecretAccessKey == null
                : discoveryAccessKeyId != null || discoverySecretAccessKey != null) {
            throw new IllegalArgumentException("火山方舟控制面发现必须且只能配置独立 AK/SK");
        }
        if (arkDiscovery && !region.matches("[a-z]{2}-[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("火山方舟区域格式不正确");
        }
    }

    /** 创建不需要控制面 AK/SK 的连接，仍须显式冻结预设和发现协议。 */
    public CreatePlatformProviderConnectionCommand(
            PlatformRequestContext context,
            String idempotencyKey,
            ProviderKind providerKind,
            PlatformModelDiscoveryProfile discoveryProfile,
            PlatformProviderPresetSelection preset,
            String displayName,
            URI endpoint,
            String region,
            AuthenticationType authenticationType,
            SecretValue apiKey) {
        this(context, idempotencyKey, providerKind, discoveryProfile, preset, displayName, endpoint, region,
                authenticationType, apiKey, null, null);
    }
}
