package com.aiplatform.appstudio.domain.port;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一收敛 app-studio 端口请求、运行时描述和召回证据的输入契约。
 */
public final class AppStudioPortContracts {
    private AppStudioPortContracts() {
    }

    public static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    public static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    public static List<String> normalizeTextList(Collection<String> values, String fieldName) {
        requireValue(values, fieldName);
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String text = requireText(value, fieldName);
            if (!normalized.contains(text)) {
                normalized.add(text);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return List.copyOf(normalized);
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

    public static Set<String> normalizeOptionalTextSet(Collection<String> values, String fieldName) {
        requireValue(values, fieldName);
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(requireText(value, fieldName));
        }
        return Collections.unmodifiableSet(normalized);
    }

    public static <T> List<T> copyRequiredList(Collection<T> values, String fieldName) {
        requireValue(values, fieldName);
        List<T> copy = new ArrayList<>();
        for (T value : values) {
            copy.add(requireElement(value, fieldName));
        }
        return List.copyOf(copy);
    }

    public static <T> Set<T> copyRequiredSet(Collection<T> values, String fieldName) {
        requireValue(values, fieldName);
        Set<T> copy = new LinkedHashSet<>();
        for (T value : values) {
            copy.add(requireElement(value, fieldName));
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

    public static Map<String, String> normalizeTextMap(Map<String, String> values, String fieldName) {
        requireValue(values, fieldName);
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = requireText(entry.getKey(), fieldName + " key");
            String value = requireText(entry.getValue(), fieldName + " value");
            if (normalized.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(fieldName + " key重复：" + key);
            }
        }
        return Map.copyOf(normalized);
    }

    public static double requireScore(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(fieldName + "必须在 0 到 1 之间");
        }
        return value;
    }

    private static <T> T requireElement(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
