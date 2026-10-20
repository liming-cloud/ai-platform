package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 平台管理员基于当前发现候选创建草稿部署的输入。 */
public record CreatePlatformModelDeploymentCommand(
        PlatformRequestContext context, String idempotencyKey, String providerConnectionId,
        long expectedConnectionVersion, String modelCode, String displayName, ModelType modelType,
        Set<ModelCapability> capabilities, int contextWindow, Integer outputLimit, Integer embeddingDimension, Integer rerankCandidateLimit,
        String parameterSource, Set<String> plannedTenantIds, Boolean modelConfirmed) {
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");
    public CreatePlatformModelDeploymentCommand {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) throw new IllegalArgumentException("幂等键格式不正确");
        providerConnectionId = Objects.requireNonNull(providerConnectionId, "平台供应商连接标识不能为空");
        modelType = Objects.requireNonNull(modelType, "模型类型不能为空");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "模型能力不能为空"));
        if (!Boolean.TRUE.equals(modelConfirmed)) throw new IllegalArgumentException("必须明确确认模型类型、能力和参数来源");
        plannedTenantIds = Set.copyOf(Objects.requireNonNull(plannedTenantIds, "必须明确选择拟授权租户"));
        if (plannedTenantIds.isEmpty()) throw new IllegalArgumentException("必须明确选择拟授权租户");
        if (parameterSource == null || parameterSource.isBlank()) throw new IllegalArgumentException("必须声明参数来源");
    }
}
