package com.aiplatform.foundation.modelruntime.domain.model;

/**
 * 租户内模型部署的稳定标识。
 */
public record ModelDeploymentId(long value) {

    public ModelDeploymentId {
        if (value <= 0) {
            throw new IllegalArgumentException("模型部署标识必须是正整数");
        }
    }

    public static ModelDeploymentId parse(String value) {
        try {
            return new ModelDeploymentId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("模型部署标识格式不正确", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
