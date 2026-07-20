package com.aiplatform.shared.kernel;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Closed, typed projection for runtime options that may enter model approval evidence.
 * Nested objects are deliberately unsupported so an allowed top-level key cannot smuggle credentials.
 */
public final class ModelApprovalRuntimeOptions {
    private static final int MAX_OPTIONS = 48;
    private static final int MAX_STRING_BYTES = 2_048;
    private static final int MAX_STOP_SEQUENCES = 64;
    private static final int MAX_STOP_SEQUENCE_BYTES = 256;
    private static final int MAX_NUMBER_CHARS = 128;

    private static final Set<String> STRING_KEYS = Set.of(
            "providerKind", "encodingFormat", "keepAlive", "deploymentName", "apiVersion",
            "serviceVersion", "user", "reasoningEffort", "region", "embeddingProvider",
            "inputType", "projectId", "location", "apiEndpoint", "publisher", "taskType", "title",
            "truncate"
    );
    private static final Set<String> DECIMAL_KEYS = Set.of(
            "temperature", "topP", "frequencyPenalty", "presencePenalty"
    );
    private static final Set<String> INTEGER_KEYS = Set.of(
            "topK", "maxTokens", "dimensions", "randomSeed", "n", "numCtx", "numBatch",
            "numThread", "numPredict", "timeoutSeconds"
    );
    private static final Set<String> BOOLEAN_KEYS = Set.of(
            "safePrompt", "autoTruncate"
    );
    private static final Set<String> LIST_OF_STRING_KEYS = Set.of("stopSequences");
    private static final Set<String> ALL_KEYS;

    static {
        Set<String> keys = new TreeSet<>();
        keys.addAll(STRING_KEYS);
        keys.addAll(DECIMAL_KEYS);
        keys.addAll(INTEGER_KEYS);
        keys.addAll(BOOLEAN_KEYS);
        keys.addAll(LIST_OF_STRING_KEYS);
        ALL_KEYS = Set.copyOf(keys);
    }

    private ModelApprovalRuntimeOptions() {
    }

    public static Map<String, Object> validateAndNormalize(Map<?, ?> source) {
        Objects.requireNonNull(source, "模型审批 runtimeOptions 不能为空");
        if (source.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("模型审批 runtimeOptions 字段数量超过上限 " + MAX_OPTIONS);
        }
        Map<String, Object> projected = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new IllegalArgumentException("模型审批 runtimeOptions key 必须是非空字符串");
            }
            if (!key.equals(key.trim()) || !ALL_KEYS.contains(key)) {
                throw new IllegalArgumentException("模型审批 runtimeOptions 含未知或敏感字段：" + key);
            }
            Object value = entry.getValue();
            if (STRING_KEYS.contains(key)) {
                projected.put(key, requireString(value, key, MAX_STRING_BYTES));
            } else if (DECIMAL_KEYS.contains(key)) {
                projected.put(key, requireEffectiveDouble(value, key));
            } else if (INTEGER_KEYS.contains(key)) {
                projected.put(key, requireInteger(value, key));
            } else if (BOOLEAN_KEYS.contains(key)) {
                projected.put(key, requireBoolean(value, key));
            } else {
                projected.put(key, requireStringList(value, key));
            }
        }
        if (!projected.containsKey("providerKind")) {
            throw new IllegalArgumentException("模型审批 runtimeOptions.providerKind 不能为空");
        }
        ApprovalEvidenceEnvelope normalized = ApprovalEvidenceEnvelope.create(Map.of("runtimeOptions", projected));
        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) normalized.facts().get("runtimeOptions");
        return options;
    }

    public static Set<String> allowedKeys() {
        return ALL_KEYS;
    }

    private static String requireString(Object value, String key, int maxBytes) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("模型审批 runtimeOptions." + key + " 必须是非空字符串");
        }
        String normalized = text.trim();
        if (normalized.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("模型审批 runtimeOptions." + key + " 超过长度上限");
        }
        return normalized;
    }

    private static BigDecimal requireEffectiveDouble(Object value, String key) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("模型审批 runtimeOptions." + key + " 必须是数字");
        }
        BigDecimal decimal = BoundedDecimalCanonicalizer.canonicalize(
                number,
                MAX_NUMBER_CHARS,
                "模型审批 runtimeOptions." + key
        ).value();
        double runtimeValue = decimal.doubleValue();
        if (!Double.isFinite(runtimeValue)) {
            throw new IllegalArgumentException("模型审批 runtimeOptions." + key + " 超出运行时数字范围");
        }
        return BigDecimal.valueOf(runtimeValue);
    }

    private static Integer requireInteger(Object value, String key) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("模型审批 runtimeOptions." + key + " 必须是整数");
        }
        BigDecimal decimal = BoundedDecimalCanonicalizer.canonicalize(
                number,
                MAX_NUMBER_CHARS,
                "模型审批 runtimeOptions." + key
        ).value();
        try {
            return decimal.intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("模型审批 runtimeOptions." + key + " 必须是 32 位整数", exception);
        }
    }

    private static Boolean requireBoolean(Object value, String key) {
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException("模型审批 runtimeOptions." + key + " 必须是布尔值");
        }
        return bool;
    }

    private static List<String> requireStringList(Object value, String key) {
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("模型审批 runtimeOptions." + key + " 必须是字符串数组");
        }
        if (values.size() > MAX_STOP_SEQUENCES) {
            throw new IllegalArgumentException("模型审批 runtimeOptions." + key + " 条目数量超过上限 " + MAX_STOP_SEQUENCES);
        }
        return values.stream()
                .map(item -> requireString(item, key, MAX_STOP_SEQUENCE_BYTES))
                .toList();
    }
}
