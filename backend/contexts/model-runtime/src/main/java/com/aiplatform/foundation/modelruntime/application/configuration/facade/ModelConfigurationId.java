package com.aiplatform.foundation.modelruntime.application.configuration.facade;

/** 跨上下文使用的模型配置标识；领域标识在模型上下文内部重建。 */
public record ModelConfigurationId(long value) {
    public ModelConfigurationId {
        if (value <= 0) {
            throw new IllegalArgumentException("模型配置标识必须是正整数");
        }
    }
}
