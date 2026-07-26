package com.aiplatform.foundation.knowledgedata.application.port.out;

import java.util.Objects;

/**
 * 对象存储返回的可核验文本元数据。
 */
public record TextContentObjectMetadata(
        String objectKey,
        String sha256,
        long byteSize,
        String mediaType) {

    public TextContentObjectMetadata {
        Objects.requireNonNull(objectKey, "对象引用不能为空");
        Objects.requireNonNull(sha256, "内容摘要不能为空");
        if (byteSize < 1) {
            throw new IllegalArgumentException("内容字节数必须大于零");
        }
        Objects.requireNonNull(mediaType, "媒体类型不能为空");
    }
}
