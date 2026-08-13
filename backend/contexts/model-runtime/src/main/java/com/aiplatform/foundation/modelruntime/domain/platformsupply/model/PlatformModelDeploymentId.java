package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/** 平台范围模型部署的稳定标识。 */
public record PlatformModelDeploymentId(long value) implements ValueObject {

    public PlatformModelDeploymentId {
        if (value <= 0) {
            throw new IllegalArgumentException("平台模型部署标识必须是正整数");
        }
    }

    public static PlatformModelDeploymentId parse(String value) {
        try {
            return new PlatformModelDeploymentId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("平台模型部署标识格式不正确", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
