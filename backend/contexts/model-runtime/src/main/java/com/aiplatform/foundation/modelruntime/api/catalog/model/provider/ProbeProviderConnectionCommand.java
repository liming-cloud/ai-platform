package com.aiplatform.foundation.modelruntime.api.catalog.model.provider;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Duration;
import java.util.Objects;

/**
 * 使用连接当前修订执行最小模型探测。
 */
public record ProbeProviderConnectionCommand(
        TrustedRequestContext context,
        String providerConnectionId,
        long expectedVersion,
        String modelCode,
        Duration timeout) {

    public ProbeProviderConnectionCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(providerConnectionId, "供应商连接标识不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期版本不能为负数");
        }
        Objects.requireNonNull(modelCode, "探测模型代码不能为空");
        if (modelCode.isBlank() || !modelCode.equals(modelCode.trim())) {
            throw new IllegalArgumentException("探测模型代码格式不正确");
        }
        Objects.requireNonNull(timeout, "探测超时不能为空");
        if (timeout.isNegative()
                || timeout.isZero()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("探测超时必须在 0 到 30 秒之间");
        }
    }
}
