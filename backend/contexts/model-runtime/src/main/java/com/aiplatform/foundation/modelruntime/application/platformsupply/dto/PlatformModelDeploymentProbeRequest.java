package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** 平台模型能力探测的受限输入，只携带密钥引用而不携带租户身份。 */
public record PlatformModelDeploymentProbeRequest(
        ProviderKind providerKind,
        URI endpoint,
        CredentialReference credentialReference,
        String modelCode,
        Set<ModelCapability> requestedCapabilities,
        Duration timeout) {
    public PlatformModelDeploymentProbeRequest {
        providerKind = Objects.requireNonNull(providerKind, "供应商类型不能为空");
        endpoint = Objects.requireNonNull(endpoint, "供应商地址不能为空");
        credentialReference = Objects.requireNonNull(credentialReference, "密钥引用不能为空");
        modelCode = Objects.requireNonNull(modelCode, "探测模型编码不能为空");
        requestedCapabilities = Set.copyOf(Objects.requireNonNull(requestedCapabilities, "待探测模型能力不能为空"));
        timeout = Objects.requireNonNull(timeout, "探测超时不能为空");
        if (modelCode.isBlank() || modelCode.length() > 256
                || modelCode.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new IllegalArgumentException("探测模型编码格式不正确");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("探测超时必须大于零");
        }
    }
}
