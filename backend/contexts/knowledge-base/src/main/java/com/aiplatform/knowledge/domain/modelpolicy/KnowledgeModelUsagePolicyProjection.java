package com.aiplatform.knowledge.domain.modelpolicy;

import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.Sha256Digests;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库本地模型使用策略投影，来自 model-hub 领域事件，用于检索和索引阶段 fail-closed 判定。
 */
public record KnowledgeModelUsagePolicyProjection(
        TenantId tenantId,
        String modelConfigId,
        String providerId,
        String providerCode,
        String providerKind,
        KnowledgeModelRuntimeConnectionRef connectionRef,
        String modelCode,
        int modelConfigVersion,
        String modelType,
        Set<KnowledgeModelCapability> capabilities,
        Set<KnowledgeModelUseCase> allowedUseCases,
        Set<String> allowedKnowledgeBaseIds,
        Map<String, Object> effectiveOptions,
        String effectiveOptionsDigest,
        int maxInputTokens,
        int maxOutputTokens,
        String policyVersion,
        long policyRevision,
        String policyDigest,
        Instant expiresAt,
        boolean enabled,
        String sourceEventId,
        long sourceEventVersion,
        Instant lastEventAt
) implements AggregateRoot<String> {
    public KnowledgeModelUsagePolicyProjection {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        modelConfigId = requireText(modelConfigId, "模型配置 ID");
        providerId = requireText(providerId, "供应商 ID");
        providerCode = requireText(providerCode, "供应商编码");
        providerKind = requireText(providerKind, "供应商类型");
        Objects.requireNonNull(connectionRef, "模型运行时连接引用不能为空");
        modelCode = requireText(modelCode, "模型编码");
        if (modelConfigVersion < 0) {
            throw new IllegalArgumentException("模型配置版本不能小于 0");
        }
        modelType = requireText(modelType, "模型类型").toUpperCase(java.util.Locale.ROOT);
        capabilities = requireNonEmptySet(capabilities, "模型能力");
        allowedUseCases = requireNonEmptySet(allowedUseCases, "允许使用场景");
        allowedKnowledgeBaseIds = normalizeTextSet(Objects.requireNonNull(
                allowedKnowledgeBaseIds,
                "允许知识库范围不能为空"
        ), "知识库 ID");
        effectiveOptions = copyRequiredMap(effectiveOptions, "模型 options");
        effectiveOptionsDigest = Sha256Digests.requireLowercase(
                effectiveOptionsDigest,
                "模型 effective options 摘要"
        );
        if (maxInputTokens < 0 || maxOutputTokens < 0) {
            throw new IllegalArgumentException("Token 上限不能小于 0");
        }
        policyVersion = requireText(policyVersion, "策略版本");
        if (policyRevision <= 0) {
            throw new IllegalArgumentException("策略修订号必须大于 0");
        }
        policyDigest = requirePolicyDigest(policyDigest);
        sourceEventId = requireText(sourceEventId, "来源事件 ID");
        if (sourceEventVersion < 1) {
            throw new IllegalArgumentException("来源事件版本必须大于 0");
        }
        Objects.requireNonNull(lastEventAt, "最后事件时间不能为空");
    }

    @Override
    public String id() {
        return modelConfigId;
    }

    public KnowledgeModelRuntimeDescriptor runtimeDescriptor() {
        return new KnowledgeModelRuntimeDescriptor(
                modelConfigId,
                providerId,
                providerCode,
                providerKind,
                connectionRef,
                modelCode,
                modelConfigVersion,
                modelType,
                capabilities,
                effectiveOptions,
                effectiveOptionsDigest,
                policyVersion
        );
    }

    public boolean allowsKnowledgeBase(String knowledgeBaseId) {
        String normalizedKnowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        return allowedKnowledgeBaseIds.isEmpty() || allowedKnowledgeBaseIds.contains(normalizedKnowledgeBaseId);
    }

    private static Set<String> normalizeTextSet(Set<String> values, String fieldName) {
        return values.stream()
                .map(value -> requireText(value, fieldName))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static <T> Set<T> requireNonEmptySet(Set<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "不能为空");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return values.stream()
                .map(value -> requireElement(value, fieldName))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static <K, V> Map<K, V> copyRequiredMap(Map<K, V> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "不能为空");
        Map<K, V> copy = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : values.entrySet()) {
            copy.put(
                    requireElement(entry.getKey(), fieldName + " key"),
                    requireElement(entry.getValue(), fieldName + " value")
            );
        }
        return Map.copyOf(copy);
    }

    private static <T> T requireElement(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String requirePolicyDigest(String value) {
        String digest = requireText(value, "策略摘要");
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("策略摘要必须是 64 位小写 SHA-256");
        }
        return digest;
    }
}
