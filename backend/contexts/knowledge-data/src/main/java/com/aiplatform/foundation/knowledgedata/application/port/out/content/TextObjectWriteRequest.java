package com.aiplatform.foundation.knowledgedata.application.port.out.content;

import java.time.Instant;
import java.util.Objects;

/** 文本对象存储扩展的最小写入请求。 */
public record TextObjectWriteRequest(long tenantId, long knowledgeSpaceId, long dataSourceId,
        int contentVersion, StagedTextContent content, Instant createdAt) {
    public TextObjectWriteRequest {
        if (tenantId <= 0 || knowledgeSpaceId <= 0 || dataSourceId <= 0 || contentVersion < 1) {
            throw new IllegalArgumentException("文本对象范围或版本不正确");
        }
        Objects.requireNonNull(content, "暂存内容不能为空");
        Objects.requireNonNull(createdAt, "内容创建时间不能为空");
    }
}
