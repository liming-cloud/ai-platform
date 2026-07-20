package com.aiplatform.knowledge.domain;

/**
 * 已持久化对象存储后的文档对象定位信息。
 */
public record StoredDocumentObject(String bucket, String objectKey, String sourceUri) {
    public StoredDocumentObject {
        bucket = requireText(bucket, "对象存储桶");
        objectKey = requireText(objectKey, "对象存储Key");
        sourceUri = requireText(sourceUri, "文档来源地址");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
