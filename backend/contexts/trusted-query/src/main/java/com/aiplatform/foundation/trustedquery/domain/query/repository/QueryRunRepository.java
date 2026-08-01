package com.aiplatform.foundation.trustedquery.domain.query.repository;

import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryQuotaPolicy;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryRunClaim;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunEvent;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import java.util.List;
import java.util.Optional;

/**
 * 查询运行的幂等占位和租户隔离读取端口。
 */
public interface QueryRunRepository {

    QueryRunClaim claim(
            QueryRun queryRun,
            String idempotencyKey,
            String requestFingerprint,
            QueryQuotaPolicy quota);

    Optional<QueryRun> find(TenantId tenantId, QueryRunId queryRunId);

    List<QueryRunEvent> findEvents(
            TenantId tenantId,
            QueryRunId queryRunId,
            long afterSequence,
            int limit);
}
