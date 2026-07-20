package com.aiplatform.infra.objectstorage.gateway.model;

/**
 * 对象存储读取结果，保留 bucket 与 objectKey 便于业务异常定位。
 */
public record ObjectStorageContent(
        String bucket,
        String objectKey,
        byte[] content
) {
    public ObjectStorageContent {
        bucket = requireText(bucket, "bucket");
        objectKey = requireText(objectKey, "objectKey");
        content = copyContent(content);
    }

    @Override
    public byte[] content() {
        return content.clone();
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

    private static byte[] copyContent(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("对象内容不能为空");
        }
        if (content.length == 0) {
            throw new IllegalArgumentException("对象内容不能为空");
        }
        return content.clone();
    }
}
