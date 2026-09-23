package com.aiplatform.foundation.knowledgedata.application.datasource.port.out;

import java.util.Objects;

/** 已由知识上下文核对冻结集合成员资格的平台对象读取事实。 */
public record PlatformObjectReadRequest(long tenantId, long knowledgeSpaceId, String rangeId,
        long selectionRevision, String objectId, String contentDigest, long byteSize, String mediaType) {
    public PlatformObjectReadRequest {
        if (tenantId <= 0 || knowledgeSpaceId <= 0 || selectionRevision < 1 || byteSize < 1) {
            throw new IllegalArgumentException("平台对象范围或长度不正确");
        }
        Objects.requireNonNull(rangeId, "范围标识不能为空");
        Objects.requireNonNull(objectId, "对象标识不能为空");
        Objects.requireNonNull(contentDigest, "对象内容摘要不能为空");
        Objects.requireNonNull(mediaType, "对象媒体类型不能为空");
    }
}
