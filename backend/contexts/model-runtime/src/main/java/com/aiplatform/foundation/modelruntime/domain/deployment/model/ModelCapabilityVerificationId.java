package com.aiplatform.foundation.modelruntime.domain.deployment.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 模型能力验证事实的稳定标识。
 */
public record ModelCapabilityVerificationId(long value) implements ValueObject {

    public ModelCapabilityVerificationId {
        if (value <= 0) {
            throw new IllegalArgumentException("模型能力验证标识必须是正整数");
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
