package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Objects;

/** 平台管理员对当前发现候选显式确认模型类型。 */
public record ConfirmPlatformProviderModelCandidateCommand(
        PlatformRequestContext context,
        String providerConnectionId,
        long expectedConnectionVersion,
        String modelCode,
        ModelType modelType) {
    public ConfirmPlatformProviderModelCandidateCommand {
        Objects.requireNonNull(context, "平台请求上下文不能为空");
        Objects.requireNonNull(providerConnectionId, "平台供应商连接标识不能为空");
        Objects.requireNonNull(modelCode, "模型代码不能为空");
        Objects.requireNonNull(modelType, "模型类型不能为空");
        if (expectedConnectionVersion < 0) {
            throw new IllegalArgumentException("平台供应商连接修订不能为负数");
        }
    }
}
