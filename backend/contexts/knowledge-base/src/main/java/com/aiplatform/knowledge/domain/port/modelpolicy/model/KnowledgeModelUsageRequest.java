package com.aiplatform.knowledge.domain.port.modelpolicy.model;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;
import com.aiplatform.shared.security.model.PermissionCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 知识库模型使用策略请求，包含服务端主体、权限快照、知识库资源和预估用量。
 */
public record KnowledgeModelUsageRequest(
        TenantId tenantId,
        String actorId,
        String actorType,
        Set<String> roleCodes,
        Set<PermissionCode> permissionSnapshot,
        String knowledgeBaseId,
        KnowledgeModelUseCase useCase,
        KnowledgeModelCapability requiredCapability,
        String requestedModelConfigId,
        Map<String, Object> requestedOptions,
        long estimatedInputTokens,
        long estimatedOutputTokens
) implements ValueObject {
    public KnowledgeModelUsageRequest {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = requireText(actorId, "操作者 ID");
        actorType = requireText(actorType, "操作者类型");
        roleCodes = normalizeRoleCodes(roleCodes);
        permissionSnapshot = copyRequiredSet(permissionSnapshot, "权限快照");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        Objects.requireNonNull(useCase, "模型使用场景不能为空");
        Objects.requireNonNull(requiredCapability, "目标模型能力不能为空");
        requestedModelConfigId = requireText(requestedModelConfigId, "模型配置 ID");
        requestedOptions = copyRequiredMap(requestedOptions, "模型 options");
        if (estimatedInputTokens < 0 || estimatedOutputTokens < 0) {
            throw new IllegalArgumentException("预估 Token 不能小于 0");
        }
    }

    private static Set<String> normalizeRoleCodes(Set<String> values) {
        Objects.requireNonNull(values, "角色快照不能为空");
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(requireText(value, "角色编码"));
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("角色快照不能为空");
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static <T> Set<T> copyRequiredSet(Set<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "不能为空");
        Set<T> copy = new LinkedHashSet<>();
        for (T value : values) {
            copy.add(requireElement(value, fieldName));
        }
        return Collections.unmodifiableSet(copy);
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
}
