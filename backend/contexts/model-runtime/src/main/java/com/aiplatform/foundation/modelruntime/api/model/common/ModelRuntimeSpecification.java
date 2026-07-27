package com.aiplatform.foundation.modelruntime.api.model.common;

import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderKind;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.net.URI;
import java.util.Objects;

/**
 * 供应商扩展构造模型对象所需的最小、框架无关配置快照。
 */
public record ModelRuntimeSpecification(
        TenantId tenantId,
        long providerConnectionId,
        long providerRevision,
        ProviderKind providerKind,
        URI endpoint,
        SecretReference credentialReference,
        long deploymentId,
        long deploymentRevision,
        String modelCode,
        ModelType modelType,
        int contextWindow,
        Integer outputLimit,
        Integer embeddingDimension) {

    public ModelRuntimeSpecification {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(providerKind, "供应商类型不能为空");
        Objects.requireNonNull(endpoint, "供应商端点不能为空");
        Objects.requireNonNull(credentialReference, "密钥引用不能为空");
        Objects.requireNonNull(modelCode, "模型代码不能为空");
        Objects.requireNonNull(modelType, "模型类型不能为空");
        if (providerConnectionId <= 0
                || deploymentId <= 0
                || providerRevision < 1
                || deploymentRevision < 1) {
            throw new IllegalArgumentException("供应商和模型部署修订必须大于零");
        }
        if (contextWindow < 1) {
            throw new IllegalArgumentException("上下文窗口必须大于零");
        }
        if (modelCode.isBlank()) {
            throw new IllegalArgumentException("模型代码不能为空");
        }
        if (modelType == ModelType.CHAT && (outputLimit == null || outputLimit < 1)) {
            throw new IllegalArgumentException("对话运行时必须声明输出上限");
        }
        if (modelType == ModelType.EMBEDDING
                && (embeddingDimension == null || embeddingDimension < 1)) {
            throw new IllegalArgumentException("嵌入运行时必须声明向量维度");
        }
    }
}
