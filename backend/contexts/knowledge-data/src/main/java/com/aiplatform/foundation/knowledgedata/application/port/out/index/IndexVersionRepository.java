package com.aiplatform.foundation.knowledgedata.application.port.out.index;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersion;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import java.time.Instant;
import java.util.Optional;

/**
 * 索引版本快照、幂等创建和原子激活持久化端口。
 */
public interface IndexVersionRepository {

    IndexVersionCreationOutcome create(IndexVersionBuildRequest value);

    IndexVersionSlice findPage(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            String cursor,
            int limit);

    Optional<IndexVersion> findActive(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId);

    IndexVersionCreationOutcome activate(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            IndexVersionId id,
            long expectedVersion,
            String idempotencyKey,
            String requestFingerprint,
            Instant activatedAt);
}
