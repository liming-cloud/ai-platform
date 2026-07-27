package com.aiplatform.foundation.trustedquery.application.port.out;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.domain.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.QueryRunEvent;
import com.aiplatform.foundation.trustedquery.domain.QueryRunId;
import java.util.List;
import java.util.Optional;

/**
 * 查询运行的幂等占位、终态提交和租户隔离读取端口。
 */
public interface QueryRunRepository {

    QueryRunClaim claim(
            QueryRun queryRun,
            String idempotencyKey,
            String requestFingerprint,
            QueryQuotaPolicy quota);

    void complete(QueryRun queryRun, long expectedVersion);

    Optional<QueryRun> find(TenantId tenantId, QueryRunId queryRunId);

    List<QueryRunEvent> findEvents(
            TenantId tenantId,
            QueryRunId queryRunId,
            long afterSequence,
            int limit);
}
