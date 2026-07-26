package com.aiplatform.foundation.knowledgedata.application.port.out;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import java.time.Instant;

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

    IndexVersionCreationOutcome activate(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            IndexVersionId id,
            long expectedVersion,
            String idempotencyKey,
            String requestFingerprint,
            Instant activatedAt);
}
