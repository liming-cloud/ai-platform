package com.aiplatform.modelhub.domain;

import com.aiplatform.shared.kernel.ValueObject;
import java.util.Map;
import java.util.Objects;

/**
 * 模型运行时 options 值对象，承载 Spring AI 适配前的厂商无关配置。
 */
public record ModelRuntimeOptions(Map<String, Object> values) implements ValueObject {
    private static final String PROVIDER_KIND = "providerKind";

    public ModelRuntimeOptions {
        Objects.requireNonNull(values, "模型运行时 options 不能为空");
        if (!values.containsKey(PROVIDER_KIND)) {
            throw new IllegalArgumentException("模型供应商类型 providerKind 不能为空");
        }
        var normalizedValues = new java.util.LinkedHashMap<>(values);
        normalizedValues.put(PROVIDER_KIND, normalizeProviderKind(values.get(PROVIDER_KIND)));
        values = Map.copyOf(normalizedValues);
    }

    public String providerKind() {
        return (String) values.get(PROVIDER_KIND);
    }

    public Map<String, Object> springAiOptions() {
        if (!values.containsKey(PROVIDER_KIND)) {
            return values;
        }
        return values.entrySet()
                .stream()
                .filter(entry -> !PROVIDER_KIND.equals(entry.getKey()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String normalizeProviderKind(Object value) {
        if (!(value instanceof String providerKind)) {
            throw new IllegalArgumentException("模型供应商类型 providerKind 必须是字符串");
        }
        if (providerKind.isBlank()) {
            throw new IllegalArgumentException("模型供应商类型 providerKind 不能为空");
        }
        return ModelProviderKinds.canonicalize(providerKind);
    }
}
