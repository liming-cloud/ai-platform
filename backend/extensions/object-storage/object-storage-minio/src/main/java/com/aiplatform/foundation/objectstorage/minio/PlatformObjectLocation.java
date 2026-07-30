package com.aiplatform.foundation.objectstorage.minio;

import java.util.Objects;

/**
 * 部署配置中平台不透明对象与 MinIO 内部对象键的一对一映射。
 *
 * <p>该类型只允许存在于对象存储扩展，绝不能进入领域对象、持久化、HTTP 或事件载荷。</p>
 */
public record PlatformObjectLocation(
        long tenantId,
        long knowledgeSpaceId,
        String rangeId,
        long selectionRevision,
        String objectId,
        String objectKey) {

    public PlatformObjectLocation {
        if (tenantId < 1 || knowledgeSpaceId < 1 || selectionRevision < 1) {
            throw new IllegalArgumentException("平台对象位置标识必须为正数");
        }
        rangeId = requireText(rangeId, "平台对象范围标识");
        objectId = requireText(objectId, "平台对象标识");
        objectKey = requireText(objectKey, "平台对象内部位置");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + "不能为空");
        if (value.isBlank() || !value.equals(value.trim())
                || value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException(name + "格式不正确");
        }
        return value;
    }
}
