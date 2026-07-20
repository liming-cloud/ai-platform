package com.aiplatform.knowledge.domain.port.modelpolicy.model;

import com.aiplatform.shared.kernel.ModelRuntimeContracts;
import com.aiplatform.shared.kernel.Sha256Digests;
import com.aiplatform.shared.kernel.ValueObject;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 知识库本地模型运行时描述，基础设施据此创建 Spring AI Embedding 或 Chat 模型。
 */
public record KnowledgeModelRuntimeDescriptor(
        String modelConfigId,
        String providerId,
        String providerCode,
        String providerKind,
        KnowledgeModelRuntimeConnectionRef connectionRef,
        String modelCode,
        int modelConfigVersion,
        String modelType,
        Set<KnowledgeModelCapability> capabilities,
        Map<String, Object> effectiveOptions,
        String effectiveOptionsDigest,
        String policyVersion
) implements ValueObject {
    public KnowledgeModelRuntimeDescriptor {
        modelConfigId = ModelRuntimeContracts.requireModelConfigId(modelConfigId);
        providerId = ModelRuntimeContracts.requireText(providerId, "模型供应商 ID");
        providerCode = ModelRuntimeContracts.requireProviderCode(providerCode);
        providerKind = ModelRuntimeContracts.requireProviderKind(providerKind);
        Objects.requireNonNull(connectionRef, "模型运行时连接引用不能为空");
        modelCode = ModelRuntimeContracts.requireModelCode(modelCode);
        if (modelConfigVersion < 0) {
            throw new IllegalArgumentException("模型配置版本不能小于 0");
        }
        modelType = ModelRuntimeContracts.requireText(modelType, "模型类型")
                .toUpperCase(java.util.Locale.ROOT);
        capabilities = ModelRuntimeContracts.copyRequiredSet(capabilities, "模型能力");
        effectiveOptions = ModelRuntimeContracts.copyRequiredMap(effectiveOptions, "模型 options");
        effectiveOptionsDigest = Sha256Digests.requireLowercase(
                effectiveOptionsDigest,
                "模型 effective options 摘要"
        );
        policyVersion = ModelRuntimeContracts.requirePolicyVersion(policyVersion);
    }
}
