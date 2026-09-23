package com.aiplatform.foundation.modelruntime.application.extension.facade.request;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** 平台探测扩展输入只包含平台凭据引用。 */
public record PlatformProviderProbeRequest(ProviderKind providerKind, URI endpoint,
        String credentialReference, String modelCode, Duration timeout) {
    public PlatformProviderProbeRequest {
        Objects.requireNonNull(providerKind, "供应商类型不能为空");
        Objects.requireNonNull(endpoint, "供应商地址不能为空");
        if (credentialReference == null || credentialReference.isBlank()) {
            throw new IllegalArgumentException("平台凭据引用不能为空");
        }
        Objects.requireNonNull(modelCode, "模型编码不能为空");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("探测超时必须大于零");
        }
    }
}
