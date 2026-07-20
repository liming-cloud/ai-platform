package com.aiplatform.infra.springai.document.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class SpringAiDocumentReadFacts {
    private SpringAiDocumentReadFacts() {
    }

    static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }

    static byte[] copyContent(byte[] content) {
        Objects.requireNonNull(content, "文档内容不能为空");
        if (content.length == 0) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        return Arrays.copyOf(content, content.length);
    }

    static Map<String, Object> normalizeMetadata(Map<String, Object> metadata) {
        Objects.requireNonNull(metadata, "文档业务元数据不能为空");
        if (metadata.isEmpty()) {
            throw new IllegalArgumentException("文档业务元数据不能为空");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String normalizedKey = requireText(key, "文档业务元数据key");
            Object normalizedValue = normalizeMetadataValue(value, normalizedKey);
            if (normalized.putIfAbsent(normalizedKey, normalizedValue) != null) {
                throw new IllegalArgumentException("文档业务元数据key重复：" + normalizedKey);
            }
        });
        return Map.copyOf(normalized);
    }

    private static Object normalizeMetadataValue(Object value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为空");
        if (value instanceof String text) {
            return requireText(text, fieldName);
        }
        return value;
    }
}
