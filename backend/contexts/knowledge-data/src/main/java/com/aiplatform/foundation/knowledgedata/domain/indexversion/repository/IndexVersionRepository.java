package com.aiplatform.foundation.knowledgedata.domain.indexversion.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersion;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import java.time.Instant;
import java.util.Optional;

/**
 * 索引版本快照、幂等创建和原子激活持久化端口。
 */
public interface IndexVersionRepository {

    IndexVersionCreationOutcome create(IndexVersionBuildRequest value);

    IndexVersionPage findPage(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            long pageNumber,
            long pageSize);

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
