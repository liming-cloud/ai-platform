package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Duration;
import java.util.Objects;

/** 平台连接探测命令不携带租户范围。 */
public record ProbePlatformProviderConnectionCommand(
        PlatformRequestContext context,
        String providerConnectionId,
        long expectedVersion,
        String modelCode,
        Duration timeout) {
    public ProbePlatformProviderConnectionCommand {
        Objects.requireNonNull(context, "平台请求上下文不能为空");
        Objects.requireNonNull(providerConnectionId, "平台连接标识不能为空");
        Objects.requireNonNull(modelCode, "模型编码不能为空");
        Objects.requireNonNull(timeout, "探测超时不能为空");
    }
}
