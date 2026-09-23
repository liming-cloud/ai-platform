package com.aiplatform.foundation.modelruntime.application.extension.facade.request;

import java.util.Objects;

/** 平台模型发现的独立 AK/SK 引用，不包含秘密值。 */
public record PlatformProviderDiscoveryCredentialReferences(String accessKeyId, String secretAccessKey) {
    public PlatformProviderDiscoveryCredentialReferences {
        Objects.requireNonNull(accessKeyId, "Access Key ID 引用不能为空");
        Objects.requireNonNull(secretAccessKey, "Secret Access Key 引用不能为空");
        if (accessKeyId.isBlank() || secretAccessKey.isBlank()) {
            throw new IllegalArgumentException("发现凭据引用不能为空白");
        }
    }
}
