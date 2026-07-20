package com.aiplatform.contracts.capability.knowledge;

import java.util.List;
import java.util.Objects;

final class KnowledgeRetrievalContracts {
    private KnowledgeRetrievalContracts() {
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    static List<String> normalizeTextList(List<String> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        List<String> normalized = values.stream()
                .map(value -> requireText(value, fieldName))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    static <T> List<T> copyRequiredList(List<T> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return values.stream()
                .map(value -> Objects.requireNonNull(value, fieldName + "不能为空"))
                .toList();
    }

    static double requireScore(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(fieldName + "必须在 0 到 1 之间");
        }
        return value;
    }
}
