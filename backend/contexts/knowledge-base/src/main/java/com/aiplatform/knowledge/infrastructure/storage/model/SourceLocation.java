package com.aiplatform.knowledge.infrastructure.storage.model;

import java.util.Objects;

/**
 * 对象存储来源定位，表达 sourceUri 解析后的 bucket 和 object key。
 */
public record SourceLocation(String bucket, String objectKey) {
    public SourceLocation {
        Objects.requireNonNull(bucket, "bucket不能为空");
        Objects.requireNonNull(objectKey, "objectKey不能为空");
    }
}
