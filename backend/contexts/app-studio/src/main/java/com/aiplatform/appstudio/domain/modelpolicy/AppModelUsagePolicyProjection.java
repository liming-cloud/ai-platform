package com.aiplatform.appstudio.domain.modelpolicy;

import com.aiplatform.appstudio.domain.port.AppStudioPortContracts;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeConnectionRef;
import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.Sha256Digests;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * AI 应用本地模型使用策略投影，来自 model-hub 领域事件，用于聊天场景 fail-closed 判定。
 */
public record AppModelUsagePolicyProjection(
        TenantId tenantId,
        String modelConfigId,
        String providerId,
        String providerCode,
        String providerKind,
        AppModelRuntimeConnectionRef connectionRef,
        String modelCode,
        int modelConfigVersion,
        String modelType,
        Set<String> capabilities,
        Set<String> allowedUseCases,
        Set<String> allowedApplicationIds,
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
    public AppModelUsagePolicyProjection {
        tenantId = AppStudioPortContracts.requireValue(tenantId, "租户 ID");
        modelConfigId = AppStudioPortContracts.requireText(modelConfigId, "模型配置 ID");
        providerId = AppStudioPortContracts.requireText(providerId, "供应商 ID");
        providerCode = AppStudioPortContracts.requireText(providerCode, "供应商编码");
        providerKind = AppStudioPortContracts.requireText(providerKind, "供应商类型");
        connectionRef = AppStudioPortContracts.requireValue(connectionRef, "模型运行时连接引用");
        modelCode = AppStudioPortContracts.requireText(modelCode, "模型编码");
        if (modelConfigVersion < 0) {
            throw new IllegalArgumentException("模型配置版本不能小于 0");
        }
        modelType = AppStudioPortContracts.requireText(modelType, "模型类型")
                .toUpperCase(java.util.Locale.ROOT);
        capabilities = normalizeRequiredUppercaseSet(capabilities, "模型能力");
        allowedUseCases = normalizeRequiredUppercaseSet(allowedUseCases, "允许使用场景");
        allowedApplicationIds = normalizeOptionalTextSet(allowedApplicationIds, "允许应用范围");
        effectiveOptions = AppStudioPortContracts.copyRequiredMap(effectiveOptions, "模型 options");
        effectiveOptionsDigest = Sha256Digests.requireLowercase(
                effectiveOptionsDigest,
                "模型 effective options 摘要"
        );
        if (maxInputTokens < 0 || maxOutputTokens < 0) {
            throw new IllegalArgumentException("Token 上限不能小于 0");
        }
        policyVersion = AppStudioPortContracts.requireText(policyVersion, "策略版本");
        if (policyRevision <= 0) {
            throw new IllegalArgumentException("策略修订号必须大于 0");
        }
        policyDigest = requirePolicyDigest(policyDigest);
        sourceEventId = AppStudioPortContracts.requireText(sourceEventId, "来源事件 ID");
        if (sourceEventVersion <= 0) {
            throw new IllegalArgumentException("来源事件版本必须大于 0");
        }
        lastEventAt = AppStudioPortContracts.requireValue(lastEventAt, "最后事件时间");
    }

    @Override
    public String id() {
        return tenantId.value() + ":" + modelConfigId;
    }

    private static Set<String> normalizeRequiredUppercaseSet(Set<String> values, String fieldName) {
        Set<String> normalized = normalizeOptionalTextSet(values, fieldName).stream()
                .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return Set.copyOf(normalized);
    }

    private static Set<String> normalizeOptionalTextSet(Set<String> values, String fieldName) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(AppStudioPortContracts.requireText(value, fieldName));
        }
        return Set.copyOf(normalized);
    }

    private static String requirePolicyDigest(String value) {
        String digest = AppStudioPortContracts.requireText(value, "策略摘要");
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("策略摘要必须是 64 位小写 SHA-256");
        }
        return digest;
    }
}
