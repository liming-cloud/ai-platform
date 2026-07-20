package com.aiplatform.modelhub.domain.modelpolicy;

import com.aiplatform.modelhub.domain.ModelCapability;
import com.aiplatform.modelhub.domain.modelpolicy.model.ModelUseCase;
import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模型使用策略聚合根，统一约束租户、应用、用例和模型能力的可用范围。
 */
public record ModelUsagePolicy(
        TenantId tenantId,
        String policyId,
        String modelConfigId,
        Set<ModelUseCase> allowedUseCases,
        Set<ModelCapability> allowedCapabilities,
        Set<String> allowedApplicationIds,
        int maxInputTokens,
        int maxOutputTokens,
        String policyVersion,
        Instant expiresAt,
        boolean enabled
) implements AggregateRoot<String> {
    public ModelUsagePolicy {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        policyId = requireText(policyId, "策略 ID");
        modelConfigId = requireText(modelConfigId, "模型配置 ID");
        allowedUseCases = requireNonEmptySet(allowedUseCases, "允许用例");
        allowedCapabilities = requireNonEmptySet(allowedCapabilities, "允许能力");
        allowedApplicationIds = normalizeTextSet(allowedApplicationIds, "允许应用 ID");
        if (maxInputTokens < 0 || maxOutputTokens < 0) {
            throw new IllegalArgumentException("Token 上限不能小于 0");
        }
        policyVersion = requireText(policyVersion, "策略版本");
    }

    @Override
    public String id() {
        return policyId;
    }

    public boolean expiredAt(Instant now) {
        Objects.requireNonNull(now, "校验时间不能为空");
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    private static Set<String> normalizeTextSet(Set<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "集合不能为空");
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
}
