package com.aiplatform.shared.kernel;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Versioned, secret-free approval evidence whose digest is stable across JSON map order and numeric representations.
 * JSON array order and duplicate elements remain significant.
 */
public record ApprovalEvidenceEnvelope(
        String schema,
        String digest,
        Map<String, Object> facts
) implements ValueObject {
    public static final String MODEL_APPROVAL_SCHEMA_V1 = "model-approval-evidence/v1";
    private static final String CANONICAL_PREFIX = "approval-evidence-canonical/v1";
    private static final int MAX_DEPTH = 8;
    private static final int MAX_NODES = 1_024;
    private static final int MAX_MAP_ENTRIES = 128;
    private static final int MAX_LIST_ITEMS = 128;
    private static final int MAX_KEY_BYTES = 128;
    private static final int MAX_STRING_BYTES = 8_192;
    private static final int MAX_NUMBER_CHARS = 128;
    private static final int MAX_TOTAL_SCALAR_BYTES = 131_072;
    private static final int MAX_CANONICAL_BYTES = 131_072;

    public ApprovalEvidenceEnvelope {
        schema = requireText(schema, "审批证据 schema");
        if (!MODEL_APPROVAL_SCHEMA_V1.equals(schema)) {
            throw new IllegalArgumentException("审批证据 schema 不受支持：" + schema);
        }
        facts = normalizeMap(facts, "facts");
        if (facts.isEmpty()) {
            throw new IllegalArgumentException("审批证据 facts 不能为空");
        }
        digest = requireText(digest, "审批证据摘要").toLowerCase(java.util.Locale.ROOT);
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("审批证据摘要必须是 64 位 SHA-256 十六进制值");
        }
        String expected = digest(schema, facts);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                digest.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new IllegalArgumentException("审批证据摘要与 canonical facts 不一致");
        }
    }

    public static ApprovalEvidenceEnvelope create(Map<String, ?> facts) {
        Map<String, Object> normalized = normalizeMap(facts, "facts");
        return new ApprovalEvidenceEnvelope(
                MODEL_APPROVAL_SCHEMA_V1,
                digest(MODEL_APPROVAL_SCHEMA_V1, normalized),
                normalized
        );
    }

    public String canonicalFacts() {
        return canonical(schema, facts);
    }

    public static String digestFacts(Map<String, ?> facts) {
        return digest(MODEL_APPROVAL_SCHEMA_V1, normalizeMap(facts, "facts"));
    }

    private static String digest(String schema, Map<String, Object> facts) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256")
                    .digest(canonical(schema, facts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private static String canonical(String schema, Map<String, Object> facts) {
        String canonical = lengthPrefixed(CANONICAL_PREFIX)
                + lengthPrefixed(schema)
                + encode(facts);
        if (canonical.getBytes(StandardCharsets.UTF_8).length > MAX_CANONICAL_BYTES) {
            throw new IllegalArgumentException("审批证据 canonical 内容超过大小上限");
        }
        return canonical;
    }

    private static String encode(Object value) {
        if (value == null) {
            return "N";
        }
        if (value instanceof String text) {
            return "S" + lengthPrefixed(text);
        }
        if (value instanceof Boolean bool) {
            return bool ? "B1" : "B0";
        }
        if (value instanceof Number number) {
            return "D" + lengthPrefixed(canonicalNumber(number));
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder encoded = new StringBuilder("M").append(map.size()).append(':');
            map.forEach((key, child) -> encoded
                    .append(lengthPrefixed((String) key))
                    .append(lengthPrefixed(encode(child))));
            return encoded.toString();
        }
        if (value instanceof List<?> list) {
            List<String> encodedValues = list.stream()
                    .map(ApprovalEvidenceEnvelope::encode)
                    .toList();
            StringBuilder encoded = new StringBuilder("L").append(encodedValues.size()).append(':');
            encodedValues.forEach(child -> encoded.append(lengthPrefixed(child)));
            return encoded.toString();
        }
        throw new IllegalArgumentException(
                "审批证据只允许 JSON 标量、对象和数组，实际类型=" + value.getClass().getName()
        );
    }

    private static String canonicalNumber(Number number) {
        return BoundedDecimalCanonicalizer.canonicalize(
                number,
                MAX_NUMBER_CHARS,
                "审批证据数字"
        ).plainText();
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source, String fieldName) {
        return normalizeMap(
                source,
                fieldName,
                0,
                new ApprovalEvidenceNormalizationBudget(MAX_NODES, MAX_TOTAL_SCALAR_BYTES)
        );
    }

    private static Map<String, Object> normalizeMap(
            Map<?, ?> source,
            String fieldName,
            int depth,
            ApprovalEvidenceNormalizationBudget budget
    ) {
        Objects.requireNonNull(source, "审批证据 " + fieldName + " 不能为空");
        requireDepth(depth);
        budget.addNode();
        if (source.size() > MAX_MAP_ENTRIES) {
            throw new IllegalArgumentException("审批证据对象字段数量超过上限 " + MAX_MAP_ENTRIES);
        }
        Map<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("审批证据对象 key 必须是字符串");
            }
            String normalizedKey = requireText(key, "审批证据对象 key");
            budget.addScalarBytes(normalizedKey, MAX_KEY_BYTES, "审批证据对象 key");
            if (sorted.containsKey(normalizedKey)) {
                throw new IllegalArgumentException("审批证据对象包含重复 key：" + normalizedKey);
            }
            sorted.put(normalizedKey, normalizeValue(entry.getValue(), depth + 1, budget));
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Object normalizeValue(Object value, int depth, ApprovalEvidenceNormalizationBudget budget) {
        requireDepth(depth);
        if (value == null) {
            budget.addNode();
            return value;
        }
        if (value instanceof String text) {
            budget.addNode();
            budget.addScalarBytes(text, MAX_STRING_BYTES, "审批证据字符串");
            return ApprovalEvidenceSecretPolicy.requireSecretFree(text, "审批证据字符串");
        }
        if (value instanceof Boolean) {
            budget.addNode();
            return value;
        }
        if (value instanceof Number number) {
            budget.addNode();
            CanonicalDecimal canonical =
                    BoundedDecimalCanonicalizer.canonicalize(number, MAX_NUMBER_CHARS, "审批证据数字");
            budget.addScalarBytes(canonical.plainText(), MAX_NUMBER_CHARS, "审批证据数字");
            return canonical.value();
        }
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map, "map", depth, budget);
        }
        if (value instanceof List<?> list) {
            budget.addNode();
            if (list.size() > MAX_LIST_ITEMS) {
                throw new IllegalArgumentException("审批证据数组条目数量超过上限 " + MAX_LIST_ITEMS);
            }
            List<Object> normalized = new ArrayList<>(list.size());
            list.forEach(item -> normalized.add(normalizeValue(item, depth + 1, budget)));
            return java.util.Collections.unmodifiableList(new ArrayList<>(normalized));
        }
        throw new IllegalArgumentException(
                "审批证据只允许 JSON 标量、对象和数组，实际类型=" + value.getClass().getName()
        );
    }

    private static String lengthPrefixed(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return bytes.length + ":" + value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static void requireDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("审批证据嵌套深度超过上限 " + MAX_DEPTH);
        }
    }
}
