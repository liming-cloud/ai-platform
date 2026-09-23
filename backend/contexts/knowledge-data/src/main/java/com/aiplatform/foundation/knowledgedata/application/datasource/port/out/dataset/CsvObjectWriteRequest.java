package com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset;

import com.aiplatform.foundation.knowledgedata.application.port.out.content.StagedTextContent;
import java.time.Instant;
import java.util.Objects;

/** 私有 CSV 存储扩展的最小写入请求。 */
public record CsvObjectWriteRequest(long tenantId, long knowledgeSpaceId, long dataSourceId,
        long revision, StagedTextContent content, Instant createdAt) {
    public CsvObjectWriteRequest {
        if (tenantId <= 0 || knowledgeSpaceId <= 0 || dataSourceId <= 0 || revision < 1) {
            throw new IllegalArgumentException("CSV 对象范围或修订不正确");
        }
        Objects.requireNonNull(content, "暂存 CSV 不能为空");
        Objects.requireNonNull(createdAt, "创建时间不能为空");
    }
}
