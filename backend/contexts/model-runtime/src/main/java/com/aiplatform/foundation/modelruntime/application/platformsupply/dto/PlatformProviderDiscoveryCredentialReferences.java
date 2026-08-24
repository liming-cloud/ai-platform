package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import java.util.Objects;

/** 火山方舟控制面发现使用的独立 AK/SK 引用，不包含秘密明文。 */
public record PlatformProviderDiscoveryCredentialReferences(
        CredentialReference accessKeyId,
        CredentialReference secretAccessKey) {

    public PlatformProviderDiscoveryCredentialReferences {
        accessKeyId = Objects.requireNonNull(accessKeyId, "发现 Access Key ID 引用不能为空");
        secretAccessKey = Objects.requireNonNull(secretAccessKey, "发现 Secret Access Key 引用不能为空");
    }
}
