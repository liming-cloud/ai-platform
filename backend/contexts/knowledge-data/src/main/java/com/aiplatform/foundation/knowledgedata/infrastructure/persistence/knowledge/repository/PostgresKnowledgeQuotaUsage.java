package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.repository;

import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeQuotaUsage;
import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeQuotaUsageSnapshot;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.mapper.KnowledgeQuotaUsageMapper;
import java.util.Objects;

/** 只聚合知识数据上下文拥有的计量事实；不把旧受治理来源猜成有效文档。 */
public final class PostgresKnowledgeQuotaUsage implements KnowledgeQuotaUsage {

    private final KnowledgeQuotaUsageMapper mapper;

    public PostgresKnowledgeQuotaUsage(KnowledgeQuotaUsageMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "配额用量映射器不能为空");
    }

    @Override
    public KnowledgeQuotaUsageSnapshot snapshot(long tenantId) {
        if (tenantId <= 0) throw new IllegalArgumentException("租户标识必须为正数");
        return new KnowledgeQuotaUsageSnapshot(
                mapper.countKnowledgeSpaces(tenantId),
                mapper.maxDocumentsPerKnowledgeSpace(tenantId),
                mapper.maxBytesPerKnowledgeSpace(tenantId).toBigIntegerExact(),
                mapper.countUnmeteredSources(tenantId) == 0);
    }
}
