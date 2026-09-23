package com.aiplatform.foundation.modelruntime.application.runtime.facade;

import java.time.Duration;
import java.util.Objects;

/** 供应商扩展使用的模型调用超时配置。 */
public record ModelRuntimeCallSettings(Duration timeout) {
    public ModelRuntimeCallSettings {
        Objects.requireNonNull(timeout, "模型调用超时不能为空");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("模型调用超时必须大于零");
        }
    }
}
