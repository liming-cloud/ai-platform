package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/** 平台范围模型能力验证事实的稳定标识。 */
public record PlatformModelCapabilityVerificationId(long value) implements ValueObject {

    public PlatformModelCapabilityVerificationId {
        if (value <= 0) {
            throw new IllegalArgumentException("平台模型能力验证标识必须是正整数");
        }
    }
}
