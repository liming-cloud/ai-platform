package com.aiplatform.infra.objectstorage.gateway.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 待写入对象存储的通用对象。
 */
public record ObjectStorageObject(
        String objectKey,
        String contentType,
        String contentSha256,
        Map<String, String> metadata,
        byte[] content
) {
    public ObjectStorageObject {
        objectKey = requireText(objectKey, "objectKey");
        contentType = requireText(contentType, "contentType");
        contentSha256 = requireText(contentSha256, "contentSha256");
        metadata = normalizeMetadata(metadata);
        content = copyContent(content);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }

    private static Map<String, String> normalizeMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("对象元数据不能为空");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String normalizedKey = requireText(key, "对象元数据key");
            String normalizedValue = requireText(value, "对象元数据value");
            if (normalized.putIfAbsent(normalizedKey, normalizedValue) != null) {
                throw new IllegalArgumentException("对象元数据key重复：" + normalizedKey);
            }
        });
        return Map.copyOf(normalized);
    }

    private static byte[] copyContent(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("对象内容不能为空");
        }
        if (content.length == 0) {
            throw new IllegalArgumentException("对象内容不能为空");
        }
        return Arrays.copyOf(content, content.length);
    }
}
