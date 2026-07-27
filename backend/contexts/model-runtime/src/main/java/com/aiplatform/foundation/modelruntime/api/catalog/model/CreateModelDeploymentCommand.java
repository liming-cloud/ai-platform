package com.aiplatform.foundation.modelruntime.api.catalog.model;

import com.aiplatform.foundation.modelruntime.api.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.api.model.ModelType;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 在可信租户上下文中创建模型部署。
 */
public record CreateModelDeploymentCommand(
        TrustedRequestContext context,
        String idempotencyKey,
        String providerConnectionId,
        String modelCode,
        String displayName,
        ModelType modelType,
        Set<ModelCapability> capabilities,
        int contextWindow,
        Integer outputLimit,
        Integer embeddingDimension) {

    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");

    public CreateModelDeploymentCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("幂等键格式不正确");
        }
        Objects.requireNonNull(providerConnectionId, "供应商连接标识不能为空");
        Objects.requireNonNull(modelType, "模型类型不能为空");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "模型能力不能为空"));
    }
}
