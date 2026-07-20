package com.aiplatform.infra.objectstorage.gateway.model;

/**
 * 已持久化对象的定位信息。
 */
public record StoredObject(String bucket, String objectKey, String uri) {
    public StoredObject {
        bucket = requireText(bucket, "bucket");
        objectKey = requireText(objectKey, "objectKey");
        uri = requireText(uri, "uri");
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
}
