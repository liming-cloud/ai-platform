package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Duration;
import java.util.Objects;

/** 平台管理员请求固化某个平台连接当前修订的模型发现事实。 */
public record DiscoverPlatformProviderModelsCommand(
        PlatformRequestContext context,
        String providerConnectionId,
        long expectedVersion,
        Duration timeout) {

    public DiscoverPlatformProviderModelsCommand {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        providerConnectionId = Objects.requireNonNull(providerConnectionId, "平台供应商连接标识不能为空");
        timeout = Objects.requireNonNull(timeout, "模型发现超时不能为空");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("模型发现超时必须大于零");
        }
    }
}
