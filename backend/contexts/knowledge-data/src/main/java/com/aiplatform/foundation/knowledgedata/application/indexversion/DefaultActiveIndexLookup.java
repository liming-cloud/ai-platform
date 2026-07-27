package com.aiplatform.foundation.knowledgedata.application.indexversion;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.api.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.api.model.ActiveIndexSnapshot;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import java.util.Objects;
import java.util.Optional;

/**
 * 从索引生命周期投影知识空间当前活动索引快照。
 */
public final class DefaultActiveIndexLookup implements ActiveIndexLookup {

    private final IndexVersionRepository indexes;

    public DefaultActiveIndexLookup(IndexVersionRepository indexes) {
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
                        index.embedding().deploymentId(),
                        index.embedding().deploymentRevision(),
                        index.embedding().dimension()));
    }
}
