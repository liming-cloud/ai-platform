package com.aiplatform.shared.kernel;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * High-confidence, value-level guard that prevents credential material from entering approval evidence.
 * The guard also performs a bounded percent-decoding pass so encoded endpoint paths cannot hide secrets.
 */
public final class ApprovalEvidenceSecretPolicy {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_NODES = 1_024;
    private static final int MAX_COLLECTION_SIZE = 128;

    private ApprovalEvidenceSecretPolicy() {
    }

    public static String requireSecretFree(String value, String fieldName) {
        return requireSecretFree(value, fieldName, new String[0]);
    }

    public static String requireSecretFree(String value, String fieldName, String... knownCredentials) {
        String text = Objects.requireNonNull(value, fieldName + "不能为空");
        if (SensitiveMaterialDetector.containsSensitiveMaterial(text, knownCredentials)) {
            throw new IllegalArgumentException(fieldName + "疑似包含凭证或密钥材料");
        }
        return text;
    }

    public static void requireSecretFreeValue(Object value, String fieldName) {
        inspect(value, fieldName, 0, new ApprovalEvidenceSecretScanBudget(MAX_NODES), new IdentityHashMap<>());
    }

    private static void inspect(
            Object value,
            String fieldName,
            int depth,
            ApprovalEvidenceSecretScanBudget budget,
            IdentityHashMap<Object, Boolean> visiting
    ) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(fieldName + "嵌套深度超过上限 " + MAX_DEPTH);
        }
        budget.addNode();
        if (value == null || value instanceof Boolean || value instanceof Number) {
            return;
        }
        if (value instanceof String text) {
            requireSecretFree(text, fieldName);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            requireCollectionBounds(map, fieldName, visiting);
            map.forEach((key, child) -> inspect(child, fieldName + "." + key, depth + 1, budget, visiting));
            visiting.remove(map);
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            requireCollectionBounds(iterable, fieldName, visiting);
            int index = 0;
            for (Object child : iterable) {
                if (index >= MAX_COLLECTION_SIZE) {
                    throw new IllegalArgumentException(fieldName + "条目数量超过上限 " + MAX_COLLECTION_SIZE);
                }
                inspect(child, fieldName + "[" + index + "]", depth + 1, budget, visiting);
                index++;
            }
            visiting.remove(iterable);
        }
    }

    private static void requireCollectionBounds(
            Object collection,
            String fieldName,
            IdentityHashMap<Object, Boolean> visiting
    ) {
        if (visiting.put(collection, Boolean.TRUE) != null) {
            throw new IllegalArgumentException(fieldName + "包含循环引用");
        }
        if (collection instanceof Map<?, ?> map && map.size() > MAX_COLLECTION_SIZE) {
            throw new IllegalArgumentException(fieldName + "字段数量超过上限 " + MAX_COLLECTION_SIZE);
        }
    }
}
