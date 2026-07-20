package com.aiplatform.shared.kernel;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 收敛模型运行时核心事实的共享输入契约，避免不同上下文分别维护同一套校验与归一化逻辑。
 */
public final class ModelRuntimeContracts {
    private ModelRuntimeContracts() {
    }

    public static String requireModelConfigId(String value) {
        return requireText(value, "模型配置 ID");
    }

    public static String requireProviderCode(String value) {
        return requireText(value, "供应商编码");
    }

    public static String requireProviderKind(String value) {
        return requireText(value, "供应商类型");
    }

    public static String requireModelCode(String value) {
        return requireText(value, "模型编码");
    }

    public static String requirePolicyVersion(String value) {
        return requireText(value, "策略版本");
    }

    public static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    public static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    public static Set<String> normalizeTextSet(Collection<String> values, String fieldName) {
        requireValue(values, fieldName);
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(requireText(value, fieldName));
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return Collections.unmodifiableSet(normalized);
    }

    public static <T> Set<T> copyRequiredSet(Collection<T> values, String fieldName) {
        requireValue(values, fieldName);
        Set<T> copy = new LinkedHashSet<>();
        for (T value : values) {
            copy.add(requireElement(value, fieldName));
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return Collections.unmodifiableSet(copy);
    }

    public static <K, V> Map<K, V> copyRequiredMap(Map<K, V> values, String fieldName) {
        requireValue(values, fieldName);
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
}
