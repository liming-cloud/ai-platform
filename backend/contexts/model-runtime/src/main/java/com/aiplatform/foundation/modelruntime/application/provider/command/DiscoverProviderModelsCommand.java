package com.aiplatform.foundation.modelruntime.application.provider.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Duration;
import java.util.Objects;

/** 发现当前供应商连接修订可见模型的受控命令。 */
public record DiscoverProviderModelsCommand(
        TrustedRequestContext context,
        String providerConnectionId,
        long expectedVersion,
        Duration timeout) {
    public DiscoverProviderModelsCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(providerConnectionId, "供应商连接标识不能为空");
        if (providerConnectionId.isBlank()) {
            throw new IllegalArgumentException("供应商连接标识不能为空");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("期望连接版本不能为负数");
        }
        timeout = Objects.requireNonNull(timeout, "发现超时不能为空");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("发现超时必须大于零");
        }
    }
}
