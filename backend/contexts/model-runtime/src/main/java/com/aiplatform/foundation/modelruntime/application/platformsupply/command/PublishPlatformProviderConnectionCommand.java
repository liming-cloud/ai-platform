package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Objects;

/** 连接发布只接收不透明对象标识和预期修订，操作者来自可信平台会话。 */
public record PublishPlatformProviderConnectionCommand(
        PlatformRequestContext context, String providerConnectionId, long expectedVersion) {
    public PublishPlatformProviderConnectionCommand {
        Objects.requireNonNull(context, "平台上下文不能为空");
        PlatformProviderConnectionId.parse(providerConnectionId);
        if (expectedVersion < 0) throw new IllegalArgumentException("预期连接修订不能为负数");
    }
}
