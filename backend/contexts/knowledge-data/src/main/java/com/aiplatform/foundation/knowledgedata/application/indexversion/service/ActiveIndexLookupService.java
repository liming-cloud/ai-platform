package com.aiplatform.foundation.knowledgedata.application.indexversion.service;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.dto.ActiveIndexSnapshot;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import java.util.Objects;
import java.util.Optional;

/**
 * 从索引生命周期投影知识空间当前活动索引快照。
 */
public final class ActiveIndexLookupService implements ActiveIndexLookup {

    private final IndexVersionRepository indexes;

    public ActiveIndexLookupService(IndexVersionRepository indexes) {
        this.indexes = Objects.requireNonNull(indexes, "索引版本仓储不能为空");
    }

    @Override
    public Optional<ActiveIndexSnapshot> find(
            TenantId tenantId,
            long knowledgeSpaceId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        return indexes
                .findActive(tenantId, new KnowledgeSpaceId(knowledgeSpaceId))
                .map(index -> new ActiveIndexSnapshot(
                        index.knowledgeSpaceId().value(),
                        index.id().value(),
                        index.version(),
                        index.embedding()));
    }
}
